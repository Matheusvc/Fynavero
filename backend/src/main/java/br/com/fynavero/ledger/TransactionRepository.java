package br.com.fynavero.ledger;

import br.com.fynavero.domain.Kind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
