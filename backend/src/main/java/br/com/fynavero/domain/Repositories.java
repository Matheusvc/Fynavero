package br.com.fynavero.domain;

import br.com.fynavero.domain.Entities.*;
import br.com.fynavero.domain.Enums.Kind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class Repositories {
    private Repositories() {}

    public interface ProfileRepo extends JpaRepository<Profile, Long> {
        Optional<Profile> findByEmailIgnoreCase(String email);
    }

    public interface CatalogRepo extends JpaRepository<CatalogItem, Long> {
        List<CatalogItem> findAllByOrderBySortOrder();
    }

    public interface CategoryRepo extends JpaRepository<Category, Long> {
        List<Category> findByUserIdOrderByKindAscSortOrderAsc(Long userId);
        List<Category> findByUserIdAndKindOrderBySortOrder(Long userId, Kind kind);
        Optional<Category> findByIdAndUserId(Long id, Long userId);
        long countByUserIdAndKind(Long userId, Kind kind);
        void deleteByUserId(Long userId);
    }

    public interface RecurrenceRepo extends JpaRepository<Recurrence, Long> {
        List<Recurrence> findByUserIdOrderByDayOfMonth(Long userId);
        Optional<Recurrence> findByIdAndUserId(Long id, Long userId);
        void deleteByUserId(Long userId);
    }

    public interface IncomeSourceRepo extends JpaRepository<IncomeSource, Long> {
        List<IncomeSource> findByUserIdOrderByCreatedAt(Long userId);
        Optional<IncomeSource> findByIdAndUserId(Long id, Long userId);
        void deleteByUserId(Long userId);
    }

    public interface InstallmentPlanRepo extends JpaRepository<InstallmentPlan, Long> {
        List<InstallmentPlan> findByUserIdOrderByFirstDateDesc(Long userId);
        Optional<InstallmentPlan> findByIdAndUserId(Long id, Long userId);
        void deleteByUserId(Long userId);
    }

    public interface ImportBatchRepo extends JpaRepository<ImportBatch, Long> {
        List<ImportBatch> findByUserIdOrderByCreatedAtDesc(Long userId);
        Optional<ImportBatch> findByIdAndUserId(Long id, Long userId);
        void deleteByUserId(Long userId);
    }

    public interface MagicLinkRepo extends JpaRepository<MagicLinkToken, Long> {
        Optional<MagicLinkToken> findByTokenHash(String tokenHash);
        void deleteByUserId(Long userId);
    }

    public interface TransactionRepo extends JpaRepository<Transaction, Long> {
        @Query("""
            select t from Tx t
            where t.userId = :userId and t.deletedAt is null and t.date between :from and :to
            order by t.date desc, t.id desc""")
        List<Transaction> findRange(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

        @Query("""
            select t from Tx t
            where t.userId = :userId and t.deletedAt is null and t.date <= :to and t.kind = :kind""")
        List<Transaction> findKindUpTo(@Param("userId") Long userId, @Param("kind") Kind kind, @Param("to") LocalDate to);

        @Query("select t from Tx t where t.userId = :userId and t.deletedAt is null")
        List<Transaction> findAllActive(@Param("userId") Long userId);

        @Query("select count(t) > 0 from Tx t where t.userId = :userId and t.deletedAt is null and t.date <= :until")
        boolean hasAnyUntil(@Param("userId") Long userId, @Param("until") LocalDate until);

        Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

        List<Transaction> findByIdInAndUserId(Collection<Long> ids, Long userId);

        @Query("""
            select t from Tx t where t.userId = :userId and t.deletedAt is null
            and t.installmentPlanId = :planId order by t.date""")
        List<Transaction> findByPlan(@Param("userId") Long userId, @Param("planId") Long planId);

        @Query("""
            select t from Tx t where t.userId = :userId and t.deletedAt is null
            and t.installmentPlanId is not null""")
        List<Transaction> findAllInstallments(@Param("userId") Long userId);

        @Query("""
            select t from Tx t where t.userId = :userId and t.deletedAt is null
            and t.recurrenceId = :recurrenceId and t.date >= :from order by t.date""")
        List<Transaction> findRecurrenceFrom(@Param("userId") Long userId, @Param("recurrenceId") Long recurrenceId,
                                             @Param("from") LocalDate from);

        @Query("select t from Tx t where t.userId = :userId and t.importBatchId = :batchId")
        List<Transaction> findByBatch(@Param("userId") Long userId, @Param("batchId") Long batchId);

        @Modifying
        @Query("delete from Tx t where t.deletedAt is not null and t.deletedAt < :before")
        int purgeAllDeleted(@Param("before") Instant before);

        void deleteByUserId(Long userId);
    }
}
