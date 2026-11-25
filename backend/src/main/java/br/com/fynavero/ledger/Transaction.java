package br.com.fynavero.ledger;

import br.com.fynavero.ledger.Transaction.Kind;
import br.com.fynavero.ledger.Transaction.Origin;
import br.com.fynavero.ledger.Transaction.PaymentMethod;
import br.com.fynavero.ledger.Transaction.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Lançamento (despesa, receita ou investimento), sempre em centavos e positivo.
 * Excluir apenas marca {@link #deletedAt} (para o "Desfazer"); a limpeza definitiva roda à noite.
 */
@Entity
@Table(name = "transactions")
public class Transaction {
    /** Tipo de lançamento e de categoria. */
    public enum Kind { DESPESA, RECEITA, INVESTIMENTO }

    /** Realizado (já aconteceu) ou Previsto (data futura, recorrência ou parcela). */
    public enum Status { REALIZADO, PREVISTO }

    /** Como o lançamento foi criado. */
    public enum Origin { MANUAL, IMPORTADO }

    /** Formas de pagamento aceitas. */
    public enum PaymentMethod { PIX, DINHEIRO, DEBITO, CREDITO, VA_VR, VT, BOLETO, TRANSFERENCIA }

    /**
     * "Só este", "Este e os próximos" ou "Todas" ao editar ou excluir lançamentos recorrentes e parcelados.
     * ALL só existe na exclusão de parcelas: apaga a compra parcelada inteira (todas as parcelas).
     */
    public enum Scope { ONE, FOLLOWING, ALL }

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

    /** Todas as consultas filtram pelo dono dos dados (user_id) e ignoram lançamentos excluídos. */
    public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
        @Query("""
            select t from Transaction t
            where t.userId = :userId and t.deletedAt is null and t.date between :from and :to
            order by t.date desc, t.id desc""")
        List<Transaction> findRange(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
    
        @Query("""
            select t from Transaction t
            where t.userId = :userId and t.deletedAt is null and t.date <= :to and t.kind = :kind""")
        List<Transaction> findKindUpTo(@Param("userId") Long userId, @Param("kind") Kind kind, @Param("to") LocalDate to);
    
        @Query("select t from Transaction t where t.userId = :userId and t.deletedAt is null")
        List<Transaction> findAllActive(@Param("userId") Long userId);
    
        @Query("select count(t) > 0 from Transaction t where t.userId = :userId and t.deletedAt is null and t.date <= :until")
        boolean hasAnyUntil(@Param("userId") Long userId, @Param("until") LocalDate until);
    
        @Query("""
            select t from Transaction t where t.userId = :userId and t.deletedAt is null and t.categoryId = :categoryId
            order by t.date desc, t.id desc""")
        List<Transaction> findByCategory(@Param("userId") Long userId, @Param("categoryId") Long categoryId);
    
        Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
    
        /** Inclui excluídos: usado para desfazer a exclusão. */
        List<Transaction> findByIdInAndUserId(Collection<Long> ids, Long userId);
    
        @Query("""
            select t from Transaction t where t.userId = :userId and t.deletedAt is null
            and t.installmentPlanId = :planId order by t.date""")
        List<Transaction> findByPlan(@Param("userId") Long userId, @Param("planId") Long planId);
    
        @Query("""
            select t from Transaction t where t.userId = :userId and t.deletedAt is null
            and t.installmentPlanId is not null""")
        List<Transaction> findAllInstallments(@Param("userId") Long userId);
    
        @Query("""
            select t from Transaction t where t.userId = :userId and t.deletedAt is null
            and t.recurrenceId = :recurrenceId and t.date >= :from order by t.date""")
        List<Transaction> findRecurrenceFrom(@Param("userId") Long userId, @Param("recurrenceId") Long recurrenceId,
                                             @Param("from") LocalDate from);
    
        @Query("select t from Transaction t where t.userId = :userId and t.importBatchId = :batchId")
        List<Transaction> findByBatch(@Param("userId") Long userId, @Param("batchId") Long batchId);
    
        @Modifying
        @Query("delete from Transaction t where t.deletedAt is not null and t.deletedAt < :before")
        int purgeDeletedBefore(@Param("before") Instant before);
    
        void deleteByUserId(Long userId);
    }
}
