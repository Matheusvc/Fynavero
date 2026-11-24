package br.com.fynavero.account;

import br.com.fynavero.common.EmailLayout;
import br.com.fynavero.common.Mailer;
import br.com.fynavero.common.SecureTokens;
import br.com.fynavero.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static br.com.fynavero.common.Errors.badRequest;
import static br.com.fynavero.common.Errors.notFound;

/**
 * Login por link enviado ao e-mail. Sem servidor de e-mail configurado (spring.mail.host),
 * o link é escrito no log do back-end — útil em desenvolvimento.
 */
@Service
public class MagicLinkService {
    private static final Duration VALIDITY = Duration.ofMinutes(15);

    private final ProfileRepository profiles;
    private final MagicLinkTokenRepository tokens;
    private final Mailer mailer;
    private final AppProperties props;

    public MagicLinkService(ProfileRepository profiles, MagicLinkTokenRepository tokens, Mailer mailer,
                            AppProperties props) {
        this.profiles = profiles;
        this.tokens = tokens;
        this.mailer = mailer;
        this.props = props;
    }

    public boolean enabled() {
        return true;
    }

    @Transactional
    public void request(String email) {
        Optional<Profile> profile = profiles.findByEmailIgnoreCase(email.strip());
        if (profile.isEmpty()) return; // mesma resposta para e-mails sem conta (não revela quem tem cadastro)
        String token = SecureTokens.newToken();

        MagicLinkToken t = new MagicLinkToken();
        t.userId = profile.get().id;
        t.tokenHash = SecureTokens.sha256(token);
        t.expiresAt = Instant.now().plus(VALIDITY);
        tokens.save(t);

        String link = props.baseUrl() + "/entrar/link?token=" + token;
        String text = "Olá!\n\nUse este link para entrar. Ele vale por 15 minutos e só pode ser usado uma vez:\n\n"
                + link + "\n\nSe não foi você, ignore este e-mail.";
        String html = EmailLayout.page("Seu link para entrar",
                "<p>Use o botão abaixo para entrar. Ele vale por 15 minutos e só pode ser usado uma vez.</p><p>"
                        + EmailLayout.button("Entrar na Fynavero", link)
                        + "</p><p style=\"color:#646464;font-size:13px\">Se não foi você, ignore este e-mail.</p>",
                props.baseUrl() + "/configuracoes#notificacoes");
        mailer.send(profile.get().email, "Seu link para entrar na Fynavero", text, html);
    }

    @Transactional
    public Profile consume(String token) {
        MagicLinkToken t = tokens.findByTokenHash(SecureTokens.sha256(token))
                .orElseThrow(() -> badRequest("Link inválido ou expirado. Peça um novo."));
        if (t.usedAt != null || t.expiresAt.isBefore(Instant.now())) {
            throw badRequest("Link inválido ou expirado. Peça um novo.");
        }
        t.usedAt = Instant.now();
        Profile p = profiles.findById(t.userId).orElseThrow(() -> notFound());
        if (p.emailVerifiedAt == null) p.emailVerifiedAt = Instant.now(); // abrir o link prova que o e-mail é dela
        return p;
    }
}
