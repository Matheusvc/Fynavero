package br.com.fynavero.account;

import br.com.fynavero.account.AccountToken.Purpose;
import br.com.fynavero.common.EmailLayout;
import br.com.fynavero.common.Mailer;
import br.com.fynavero.common.SecureTokens;
import br.com.fynavero.config.AppProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static br.com.fynavero.common.Errors.badRequest;
import static br.com.fynavero.common.Errors.notFound;

/**
 * "Esqueci minha senha" e confirmação de e-mail. Os dois mandam um link de uso único: o de senha vale 1 hora,
 * o de confirmação 2 dias. Redefinir a senha também confirma o e-mail (a pessoa provou que recebe nele) e
 * encerra as outras sessões abertas, quando as sessões ficam no banco.
 */
@Service
public class AccountEmails {
    static final Duration RESET_VALIDITY = Duration.ofHours(1);
    static final Duration VERIFY_VALIDITY = Duration.ofDays(2);
    private static final String RESET_INVALID = "Este link de redefinição não vale mais. Peça um novo.";
    private static final String VERIFY_INVALID = "Este link de confirmação não vale mais. Peça um novo em Configurações.";

    private final ProfileRepository profiles;
    private final AccountTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final Mailer mailer;
    private final AppProperties props;
    private final ObjectProvider<FindByIndexNameSessionRepository<?>> sessions;

    public AccountEmails(ProfileRepository profiles, AccountTokenRepository tokens, PasswordEncoder encoder, Mailer mailer,
                         AppProperties props, ObjectProvider<FindByIndexNameSessionRepository<?>> sessions) {
        this.profiles = profiles;
        this.tokens = tokens;
        this.encoder = encoder;
        this.mailer = mailer;
        this.props = props;
        this.sessions = sessions;
    }

    // ------------------------------------------------------------------ esqueci minha senha

    /** Envia o link se houver conta com o e-mail. A resposta é sempre a mesma (não revela quem tem cadastro). */
    @Transactional
    public void requestPasswordReset(String email) {
        profiles.findByEmailIgnoreCase(email.strip()).ifPresent(p -> {
            String link = props.baseUrl() + "/redefinir-senha?token=" + issue(p.id, Purpose.RESET_PASSWORD, RESET_VALIDITY);
            String text = "Olá!\n\nRecebemos um pedido para redefinir a senha da sua conta na Fynavero. "
                    + "Use este link em até 1 hora (ele só funciona uma vez):\n\n" + link
                    + "\n\nSe não foi você, ignore este e-mail: sua senha continua a mesma.";
            String html = EmailLayout.page("Redefinir sua senha",
                    "<p>Recebemos um pedido para redefinir a senha da sua conta. O botão vale por 1 hora e só funciona uma vez.</p><p>"
                            + EmailLayout.button("Criar nova senha", link)
                            + "</p><p style=\"color:#646464;font-size:13px\">Se não foi você, ignore este e-mail: sua senha continua a mesma.</p>",
                    props.baseUrl() + "/configuracoes#notificacoes");
            mailer.send(p.email, "Redefinir sua senha na Fynavero", text, html);
        });
    }

    @Transactional
    public Profile resetPassword(String token, String newPassword) {
        AccountToken t = consume(token, Purpose.RESET_PASSWORD, RESET_INVALID);
        Profile p = profiles.findById(t.userId).orElseThrow(() -> notFound());
        p.passwordHash = encoder.encode(newPassword);
        if (p.emailVerifiedAt == null) p.emailVerifiedAt = Instant.now();
        tokens.invalidate(p.id, Purpose.RESET_PASSWORD, Instant.now());
        endSessions(p.email);
        return p;
    }

    // ------------------------------------------------------------------ confirmação de e-mail

    @Transactional
    public void sendVerification(Profile p) {
        if (p.emailVerifiedAt != null) return;
        tokens.invalidate(p.id, Purpose.VERIFY_EMAIL, Instant.now());
        String link = props.baseUrl() + "/confirmar-email?token=" + issue(p.id, Purpose.VERIFY_EMAIL, VERIFY_VALIDITY);
        String name = p.name == null || p.name.isBlank() ? "" : ", " + p.name.strip().split("\\s+")[0];
        String text = "Olá" + name + "!\n\nConfirme seu e-mail para receber lembretes de contas e o resumo do mês:\n\n"
                + link + "\n\nO link vale por 2 dias. Se você não criou uma conta na Fynavero, ignore este e-mail.";
        String html = EmailLayout.page("Confirme seu e-mail",
                "<p>Olá" + EmailLayout.esc(name) + "! Falta só confirmar que este e-mail é seu. Assim você recebe os lembretes de contas,"
                        + " os alertas de limite e o resumo do mês.</p><p>" + EmailLayout.button("Confirmar meu e-mail", link)
                        + "</p><p style=\"color:#646464;font-size:13px\">O link vale por 2 dias. Se você não criou uma conta na Fynavero, ignore este e-mail.</p>",
                props.baseUrl() + "/configuracoes#notificacoes");
        mailer.send(p.email, "Confirme seu e-mail na Fynavero", text, html);
    }

    @Transactional
    public Profile verifyEmail(String token) {
        AccountToken t = consume(token, Purpose.VERIFY_EMAIL, VERIFY_INVALID);
        Profile p = profiles.findById(t.userId).orElseThrow(() -> notFound());
        if (p.emailVerifiedAt == null) p.emailVerifiedAt = Instant.now();
        return p;
    }

    // ------------------------------------------------------------------ apoio

    private String issue(Long userId, Purpose purpose, Duration validity) {
        String token = SecureTokens.newToken();
        AccountToken t = new AccountToken();
        t.userId = userId;
        t.purpose = purpose;
        t.tokenHash = SecureTokens.sha256(token);
        t.expiresAt = Instant.now().plus(validity);
        t.createdAt = Instant.now();
        tokens.save(t);
        return token;
    }

    private AccountToken consume(String token, Purpose purpose, String invalidMessage) {
        if (token == null || token.isBlank() || token.length() > 100) throw badRequest(invalidMessage);
        AccountToken t = tokens.findByTokenHashAndPurpose(SecureTokens.sha256(token), purpose)
                .orElseThrow(() -> badRequest(invalidMessage));
        if (t.usedAt != null || t.expiresAt.isBefore(Instant.now())) throw badRequest(invalidMessage);
        t.usedAt = Instant.now();
        return t;
    }

    /** Encerra as sessões abertas da pessoa (só quando as sessões ficam no banco, em produção). */
    private void endSessions(String email) {
        FindByIndexNameSessionRepository<?> repo = sessions.getIfAvailable();
        if (repo == null) return;
        repo.findByPrincipalName(email).keySet().forEach(repo::deleteById);
    }
}
