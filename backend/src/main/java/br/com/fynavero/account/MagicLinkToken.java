package br.com.fynavero.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Link de acesso por e-mail: guardamos só o hash SHA-256 do token, válido por 15 minutos e uso único. */
@Entity
@Table(name = "magic_link_tokens")
public class MagicLinkToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id") public Long userId;
    @Column(name = "token_hash") public String tokenHash;
    @Column(name = "expires_at") public Instant expiresAt;
    @Column(name = "used_at") public Instant usedAt;
}
