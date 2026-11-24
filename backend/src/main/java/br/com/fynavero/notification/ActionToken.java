package br.com.fynavero.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Link "Marcar como paga" enviado no lembrete. Guardamos só o hash do token; vale até 7 dias após o vencimento. */
@Entity
@Table(name = "action_tokens")
public class ActionToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id") public Long userId;
    @Column(name = "transaction_id") public Long transactionId;
    @Column(name = "token_hash") public String tokenHash;
    @Column(name = "expires_at") public Instant expiresAt;
    @Column(name = "used_at") public Instant usedAt;
}
