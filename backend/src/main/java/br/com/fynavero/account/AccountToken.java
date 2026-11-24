package br.com.fynavero.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Link de uso único enviado por e-mail (redefinir senha ou confirmar e-mail). No banco fica só o hash. */
@Entity
@Table(name = "account_tokens")
public class AccountToken {
    public enum Purpose { RESET_PASSWORD, VERIFY_EMAIL }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id") public Long userId;
    @Enumerated(EnumType.STRING) public Purpose purpose;
    @Column(name = "token_hash") public String tokenHash;
    @Column(name = "expires_at") public Instant expiresAt;
    @Column(name = "used_at") public Instant usedAt;
    @Column(name = "created_at") public Instant createdAt;
}
