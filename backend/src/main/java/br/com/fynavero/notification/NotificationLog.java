package br.com.fynavero.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Registro de um e-mail de aviso já enviado (kind + ref únicos por pessoa), para não repetir o mesmo aviso. */
@Entity
@Table(name = "notification_log")
public class NotificationLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id") public Long userId;
    public String kind;
    public String ref;
    @Column(name = "sent_at") public Instant sentAt;
}
