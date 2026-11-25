package br.com.fynavero.account;

import br.com.fynavero.account.AccountToken.AccountTokenRepository;
import br.com.fynavero.account.AccountToken.Purpose;
import br.com.fynavero.account.Profile.ProfileRepository;
import br.com.fynavero.shared.AppProperties;
import br.com.fynavero.shared.Mailer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static br.com.fynavero.shared.Errors.badRequest;
import static br.com.fynavero.shared.Errors.notFound;

/**
 * Links da conta enviados por e-mail: entrar sem senha (15 min), redefinir senha (1 h) e confirmar e-mail (2 dias).
 * Pedidos de link respondem igual exista ou não a conta, para não revelar quem tem cadastro.
 * Abrir qualquer um desses links prova que a pessoa recebe naquele e-mail, então ele passa a contar como confirmado.
 */
@Service
public class AccountLinks {
    private final ProfileRepository profiles;
    private final PasswordEncoder encoder;
    private final Mailer mailer;
    private final AppProperties props;
    private final ObjectProvider<FindByIndexNameSessionRepository<?>> sessions;
    private final AccountTokenRepository tokens;

    public AccountLinks(ProfileRepository profiles, PasswordEncoder encoder, Mailer mailer, AppProperties props, ObjectProvider<FindByIndexNameSessionRepository<?>> sessions, AccountTokenRepository tokens) {
        this.profiles = profiles;
        this.encoder = encoder;
        this.mailer = mailer;
        this.props = props;
        this.sessions = sessions;
        this.tokens = tokens;
    }

    static final Duration MAGIC_VALIDITY = Duration.ofMinutes(15);
    static final Duration RESET_VALIDITY = Duration.ofHours(1);
    static final Duration VERIFY_VALIDITY = Duration.ofDays(2);
    private static final String MAGIC_INVALID = "Link inválido ou expirado. Peça um novo.";
    private static final String RESET_INVALID = "Este link de redefinição não vale mais. Peça um novo.";
    private static final String VERIFY_INVALID = "Este link de confirmação não vale mais. Peça um novo em Configurações.";

    // ------------------------------------------------------------------ entrar sem senha

    @Transactional
    public void requestMagicLink(String email) {
        profiles.findByEmailIgnoreCase(email.strip()).ifPresent(p -> {
            String link = props.baseUrl() + "/entrar/link?token=" + issue(p.id, Purpose.MAGIC_LINK, MAGIC_VALIDITY, null);
            send(p, "Seu link para entrar na Fynavero", "Seu link para entrar",
                    "Use este link para entrar. Ele vale por 15 minutos e só pode ser usado uma vez:",
                    "Use o botão abaixo para entrar. Ele vale por 15 minutos e só pode ser usado uma vez.",
                    "Entrar na Fynavero", link, "Se não foi você, ignore este e-mail.");
        });
    }

    @Transactional
    public Profile consumeMagicLink(String token) {
        return confirmed(consume(token, Purpose.MAGIC_LINK, MAGIC_INVALID).userId);
    }

    // ------------------------------------------------------------------ esqueci minha senha

    @Transactional
    public void requestPasswordReset(String email) {
        profiles.findByEmailIgnoreCase(email.strip()).ifPresent(p -> {
            String link = props.baseUrl() + "/redefinir-senha?token=" + issue(p.id, Purpose.RESET_PASSWORD, RESET_VALIDITY, null);
            send(p, "Redefinir sua senha na Fynavero", "Redefinir sua senha",
                    "Recebemos um pedido para redefinir a senha da sua conta na Fynavero. Use este link em até 1 hora (ele só funciona uma vez):",
                    "Recebemos um pedido para redefinir a senha da sua conta. O botão vale por 1 hora e só funciona uma vez.",
                    "Criar nova senha", link, "Se não foi você, ignore este e-mail: sua senha continua a mesma.");
        });
    }

    /** Troca a senha, invalida outros links de senha e encerra as sessões abertas (quando ficam no banco). */
    @Transactional
    public Profile resetPassword(String token, String newPassword) {
        Profile p = confirmed(consume(token, Purpose.RESET_PASSWORD, RESET_INVALID).userId);
        p.passwordHash = encoder.encode(newPassword);
        invalidate(p.id, Purpose.RESET_PASSWORD);
        FindByIndexNameSessionRepository<?> repo = sessions.getIfAvailable();
        if (repo != null) repo.findByPrincipalName(p.email).keySet().forEach(repo::deleteById);
        return p;
    }

    // ------------------------------------------------------------------ confirmação de e-mail

    @Transactional
    public void sendVerification(Profile p) {
        if (p.emailVerifiedAt != null) return;
        invalidate(p.id, Purpose.VERIFY_EMAIL);
        String link = props.baseUrl() + "/confirmar-email?token=" + issue(p.id, Purpose.VERIFY_EMAIL, VERIFY_VALIDITY, null);
        send(p, "Confirme seu e-mail na Fynavero", "Confirme seu e-mail",
                "Confirme seu e-mail para receber lembretes de contas e o resumo do mês (o link vale por 2 dias):",
                "Falta só confirmar que este e-mail é seu. Assim você recebe os lembretes de contas, os alertas de limite e o resumo do mês.",
                "Confirmar meu e-mail", link, "Se você não criou uma conta na Fynavero, ignore este e-mail.");
    }

    @Transactional
    public Profile verifyEmail(String token) {
        return confirmed(consume(token, Purpose.VERIFY_EMAIL, VERIFY_INVALID).userId);
    }

    // ------------------------------------------------------------------ apoio

    private Profile confirmed(Long userId) {
        Profile p = profiles.findById(userId).orElseThrow(() -> notFound());
        if (p.emailVerifiedAt == null) p.emailVerifiedAt = Instant.now();
        return p;
    }

    /** E-mail curto com um botão: mesma estrutura para os três links. */
    private void send(Profile p, String subject, String title, String textIntro, String htmlIntro, String buttonLabel,
                      String link, String footnote) {
        String text = "Olá, " + p.firstName() + "!\n\n" + textIntro + "\n\n" + link + "\n\n" + footnote;
        String html = Mailer.page(title,
                "<p>Olá, " + Mailer.esc(p.firstName()) + "! " + Mailer.esc(htmlIntro) + "</p><p>"
                        + Mailer.button(buttonLabel, link)
                        + "</p><p style=\"color:#646464;font-size:13px\">" + Mailer.esc(footnote) + "</p>",
                props.baseUrl() + "/configuracoes#notificacoes");
        mailer.send(p.email, subject, text, html);
    }

    // ================================================================ Tokens dos links: 256 bits aleatórios, só o hash SHA-256 no banco, validade e uso único
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Cria o token e devolve o valor que vai no link (não é guardado em lugar nenhum). */
    @Transactional
    public String issue(Long userId, Purpose purpose, Duration validity, Long refId) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        AccountToken t = new AccountToken();
        t.userId = userId;
        t.purpose = purpose;
        t.refId = refId;
        t.tokenHash = sha256(token);
        t.expiresAt = Instant.now().plus(validity);
        t.createdAt = Instant.now();
        tokens.save(t);
        return token;
    }

    /** Valida e marca como usado; um segundo uso do mesmo link falha. */
    @Transactional
    public AccountToken consume(String token, Purpose purpose, String invalidMessage) {
        AccountToken t = find(token, purpose, invalidMessage);
        if (t.usedAt != null) throw badRequest(invalidMessage);
        t.usedAt = Instant.now();
        return t;
    }

    /** Valida sem exigir que esteja sem uso (ex.: abrir de novo o link de uma conta já paga mostra "Conta paga"). */
    @Transactional(readOnly = true)
    public AccountToken peek(String token, Purpose purpose, String invalidMessage) {
        return find(token, purpose, invalidMessage);
    }

    /** Invalida os links ainda abertos daquele tipo (ao pedir um novo ou ao usar um). */
    @Transactional
    public void invalidate(Long userId, Purpose purpose) {
        tokens.invalidate(userId, purpose, Instant.now());
    }

    private AccountToken find(String token, Purpose purpose, String invalidMessage) {
        if (token == null || token.isBlank() || token.length() > 100) throw badRequest(invalidMessage);
        AccountToken t = tokens.findByTokenHashAndPurpose(sha256(token), purpose).orElseThrow(() -> badRequest(invalidMessage));
        if (t.expiresAt.isBefore(Instant.now())) throw badRequest(invalidMessage);
        return t;
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
