package br.com.fynavero.ledger;

import br.com.fynavero.ledger.Transaction.Kind;
import br.com.fynavero.ledger.Transaction.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Modelo de lançamento mensal (conta fixa, renda, "Repetir todo mês"). As ocorrências são geradas
 * como lançamentos Previsto até {@link #generatedThrough}, sob demanda, e encerradas em {@link #endDate}.
 */
@Entity
@Table(name = "recurrences")
public class Recurrence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id") public Long userId;
    @Enumerated(EnumType.STRING) public Kind kind;
    @Column(name = "category_id") public Long categoryId;
    public String description;
    @Column(name = "amount_cents") public long amountCents;
    @Column(name = "day_of_month") public int dayOfMonth;
    @Enumerated(EnumType.STRING) @Column(name = "payment_method") public PaymentMethod paymentMethod;
    @Column(name = "start_date") public LocalDate startDate;
    @Column(name = "end_date") public LocalDate endDate;
    @Column(name = "generated_through") public LocalDate generatedThrough;
    /** Conta fixa de despesa (aparece na linha do tempo de Parcelas e Dívidas). */
    @Column(name = "fixed_bill") public boolean fixedBill;
    @Column(name = "created_at") public Instant createdAt;
}
