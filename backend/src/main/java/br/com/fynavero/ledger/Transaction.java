package br.com.fynavero.ledger;

import br.com.fynavero.ledger.Kind;
import br.com.fynavero.ledger.Origin;
import br.com.fynavero.ledger.PaymentMethod;
import br.com.fynavero.ledger.Status;
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
 * Lançamento (despesa, receita ou investimento), sempre em centavos e positivo.
 * Excluir apenas marca {@link #deletedAt} (para o "Desfazer"); a limpeza definitiva roda à noite.
 */
@Entity
@Table(name = "transactions")
public class Transaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id") public Long userId;
    @Enumerated(EnumType.STRING) public Kind kind;
    @Column(name = "tx_date") public LocalDate date;
    public String description;
    @Column(name = "category_id") public Long categoryId;
    @Column(name = "amount_cents") public long amountCents;
    @Enumerated(EnumType.STRING) @Column(name = "payment_method") public PaymentMethod paymentMethod;
    @Enumerated(EnumType.STRING) public Status status;
    @Column(name = "installment_number") public Integer installmentNumber;
    @Column(name = "installment_total") public Integer installmentTotal;
    @Column(name = "recurrence_id") public Long recurrenceId;
    @Column(name = "installment_plan_id") public Long installmentPlanId;
    @Enumerated(EnumType.STRING) public Origin origin;
    @Column(name = "import_batch_id") public Long importBatchId;
    public String note;
    /** Etiquetas em minúsculas, sem "#", separadas por vírgula. Use {@link Tags}. */
    public String tags;
    @Column(name = "deleted_at") public Instant deletedAt;
    @Column(name = "created_at") public Instant createdAt;

    /** Cópia sem id, com data de criação nova. */
    public Transaction copy() {
        Transaction t = new Transaction();
        t.userId = userId;
        t.kind = kind;
        t.date = date;
        t.description = description;
        t.categoryId = categoryId;
        t.amountCents = amountCents;
        t.paymentMethod = paymentMethod;
        t.status = status;
        t.installmentNumber = installmentNumber;
        t.installmentTotal = installmentTotal;
        t.recurrenceId = recurrenceId;
        t.installmentPlanId = installmentPlanId;
        t.origin = origin;
        t.importBatchId = importBatchId;
        t.note = note;
        t.tags = tags;
        t.createdAt = Instant.now();
        return t;
    }
}
