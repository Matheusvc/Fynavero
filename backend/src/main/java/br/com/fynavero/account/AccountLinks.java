package br.com.fynavero.account;

import br.com.fynavero.account.AccountToken.Purpose;
import br.com.fynavero.shared.Mailer;
import br.com.fynavero.shared.AppProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static br.com.fynavero.shared.Errors.notFound;

/**
 * Links da conta enviados por e-mail: entrar sem senha (15 min), redefinir senha (1 h) e confirmar e-mail (2 dias).
 * Pedidos de link respondem igual exista ou não a conta, para não revelar quem tem cadastro.
 * Abrir qualquer um desses links prova que a pessoa recebe naquele e-mail, então ele passa a contar como confirmado.
 */
@Service
public class AccountLinks {
    static final Duration MAGIC_VALIDITY = Duration.ofMinutes(15);
    static final Duration RESET_VALIDITY = Duration.ofHours(1);
    static final Duration VERIFY_VALIDITY = Duration.ofDays(2);
    private static final String MAGIC_INVALID = "Link inválido ou expirado. Peça um novo.";
    private static final String RESET_INVALID = "Este link de redefinição não vale mais. Peça um novo.";
    private static final String VERIFY_INVALID = "Este link de confirmação não vale mais. Peça um novo em Configurações.";

    private final ProfileRepository profiles;
    private final OneTimeLinks links;
    private final PasswordEncoder encoder;
    private final Mailer mailer;
    private final AppProperties props;
    private final ObjectProvider<FindByIndexNameSessionRepository<?>> sessions;

    public AccountLinks(ProfileRepository profiles, OneTimeLinks links, PasswordEncoder encoder, Mailer mailer,
                        AppProperties props, ObjectProvider<FindByIndexNameSessionRepository<?>> sessions) {
        this.profiles = profiles;
        this.links = links;
        this.encoder = encoder;
        this.mailer = mailer;
        this.props = props;
        this.sessions = sessions;
    }

    // ------------------------------------------------------------------ entrar sem senha

    @Transactional
    public void requestMagicLink(String email) {
        profiles.findByEmailIgnoreCase(email.strip()).ifPresent(p -> {
            String link = props.baseUrl() + "/entrar/link?token=" + links.issue(p.id, Purpose.MAGIC_LINK, MAGIC_VALIDITY, null);
            send(p, "Seu link para entrar na Fynavero", "Seu link para entrar",
                    "Use este link para entrar. Ele vale por 15 minutos e só pode ser usado uma vez:",
                    "Use o botão abaixo para entrar. Ele vale por 15 minutos e só pode ser usado uma vez.",
                    "Entrar na Fynavero", link, "Se não foi você, ignore este e-mail.");
        });
    }

    @Transactional
    public Profile consumeMagicLink(String token) {
        return confirmed(links.consume(token, Purpose.MAGIC_LINK, MAGIC_INVALID).userId);
    }

    // ------------------------------------------------------------------ esqueci minha senha

    @Transactional
    public void requestPasswordReset(String email) {
        profiles.findByEmailIgnoreCase(email.strip()).ifPresent(p -> {
            String link = props.baseUrl() + "/redefinir-senha?token=" + links.issue(p.id, Purpose.RESET_PASSWORD, RESET_VALIDITY, null);
            send(p, "Redefinir sua senha na Fynavero", "Redefinir sua senha",
                    "Recebemos um pedido para redefinir a senha da sua conta na Fynavero. Use este link em até 1 hora (ele só funciona uma vez):",
                    "Recebemos um pedido para redefinir a senha da sua conta. O botão vale por 1 hora e só funciona uma vez.",
                    "Criar nova senha", link, "Se não foi você, ignore este e-mail: sua senha continua a mesma.");
        });
    }

    /** Troca a senha, invalida outros links de senha e encerra as sessões abertas (quando ficam no banco). */
    @Transactional
    public Profile resetPassword(String token, String newPassword) {
        Profile p = confirmed(links.consume(token, Purpose.RESET_PASSWORD, RESET_INVALID).userId);
        p.passwordHash = encoder.encode(newPassword);
        links.invalidate(p.id, Purpose.RESET_PASSWORD);
        FindByIndexNameSessionRepository<?> repo = sessions.getIfAvailable();
        if (repo != null) repo.findByPrincipalName(p.email).keySet().forEach(repo::deleteById);
        return p;
    }

    // ------------------------------------------------------------------ confirmação de e-mail

    @Transactional
    public void sendVerification(Profile p) {
        if (p.emailVerifiedAt != null) return;
        links.invalidate(p.id, Purpose.VERIFY_EMAIL);
        String link = props.baseUrl() + "/confirmar-email?token=" + links.issue(p.id, Purpose.VERIFY_EMAIL, VERIFY_VALIDITY, null);
        send(p, "Confirme seu e-mail na Fynavero", "Confirme seu e-mail",
                "Confirme seu e-mail para receber lembretes de contas e o resumo do mês (o link vale por 2 dias):",
                "Falta só confirmar que este e-mail é seu. Assim você recebe os lembretes de contas, os alertas de limite e o resumo do mês.",
                "Confirmar meu e-mail", link, "Se você não criou uma conta na Fynavero, ignore este e-mail.");
    }

    @Transactional
    public Profile verifyEmail(String token) {
        return confirmed(links.consume(token, Purpose.VERIFY_EMAIL, VERIFY_INVALID).userId);
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
}
