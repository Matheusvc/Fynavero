package br.com.fynavero.account;

import br.com.fynavero.account.AccountToken.Purpose;
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

import static br.com.fynavero.shared.Errors.badRequest;

/**
 * Tokens dos links enviados por e-mail (entrar sem senha, redefinir senha, confirmar e-mail, "Marcar como paga"):
 * 256 bits aleatórios entregues só no e-mail; no banco fica o hash SHA-256, com validade e uso único.
 */
@Service
public class OneTimeLinks {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountTokenRepository tokens;

    public OneTimeLinks(AccountTokenRepository tokens) {
        this.tokens = tokens;
    }

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
