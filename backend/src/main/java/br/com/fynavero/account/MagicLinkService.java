package br.com.fynavero.account;

import br.com.fynavero.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import static br.com.fynavero.common.Errors.badRequest;
import static br.com.fynavero.common.Errors.notFound;

/**
 * Login por link enviado ao e-mail. Sem servidor de e-mail configurado (spring.mail.host),
 * o link é escrito no log do back-end — útil em desenvolvimento.
 */
@Service
public class MagicLinkService {
    private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration VALIDITY = Duration.ofMinutes(15);

    private final ProfileRepository profiles;
    private final MagicLinkTokenRepository tokens;
    private final ObjectProvider<JavaMailSender> mail;
    private final AppProperties props;

    public MagicLinkService(ProfileRepository profiles, MagicLinkTokenRepository tokens,
                            ObjectProvider<JavaMailSender> mail, AppProperties props) {
        this.profiles = profiles;
        this.tokens = tokens;
        this.mail = mail;
        this.props = props;
    }

    public boolean enabled() {
        return true;
    }

    @Transactional
    public void request(String email) {
        Optional<Profile> profile = profiles.findByEmailIgnoreCase(email.strip());
        if (profile.isEmpty()) return; // mesma resposta para e-mails sem conta (não revela quem tem cadastro)
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        MagicLinkToken t = new MagicLinkToken();
        t.userId = profile.get().id;
        t.tokenHash = sha256(token);
        t.expiresAt = Instant.now().plus(VALIDITY);
        tokens.save(t);

        String link = props.baseUrl() + "/entrar/link?token=" + token;
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null) {
            log.info("Link mágico (sem servidor de e-mail configurado) para {}: {}", profile.get().email, link);
            return;
        }
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(props.mailFrom());
        m.setTo(profile.get().email);
        m.setSubject("Seu link para entrar na Fynavero");
        m.setText("Olá!\n\nUse este link para entrar. Ele vale por 15 minutos e só pode ser usado uma vez:\n\n"
                + link + "\n\nSe não foi você, ignore este e-mail.");
        sender.send(m);
    }

    @Transactional
    public Profile consume(String token) {
        MagicLinkToken t = tokens.findByTokenHash(sha256(token))
                .orElseThrow(() -> badRequest("Link inválido ou expirado. Peça um novo."));
        if (t.usedAt != null || t.expiresAt.isBefore(Instant.now())) {
            throw badRequest("Link inválido ou expirado. Peça um novo.");
        }
        t.usedAt = Instant.now();
        return profiles.findById(t.userId).orElseThrow(() -> notFound());
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
