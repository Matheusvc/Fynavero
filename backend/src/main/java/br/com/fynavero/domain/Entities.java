package br.com.fynavero.domain;

import br.com.fynavero.domain.Enums.Kind;
import br.com.fynavero.domain.Enums.Objective;
import br.com.fynavero.domain.Enums.Origin;
import br.com.fynavero.domain.Enums.PaymentMethod;
import br.com.fynavero.domain.Enums.Status;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Entidades sem associações JPA: chaves estrangeiras como ids simples, sempre acompanhadas de user_id,
 * para que toda consulta seja explicitamente filtrada pelo dono dos dados.
 */
public final class Entities {
    private Entities() {}

    @Entity
    @Table(name = "profiles")
    public static class Profile {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public String email;
        @Column(name = "password_hash") public String passwordHash;
        public String name;
        @Enumerated(EnumType.STRING) public Objective objective;
        @Column(name = "onboarding_done") public boolean onboardingDone;
        @Column(name = "contribution_enabled") public boolean contributionEnabled;
        @Column(name = "contribution_bps") public int contributionBps = 1000;
        @Column(name = "contribution_category_id") public Long contributionCategoryId;
        @Column(name = "terms_accepted_at") public Instant termsAcceptedAt;
        @Column(name = "created_at") public Instant createdAt;
    }

    @Entity
    @Table(name = "category_catalog")
    public static class CatalogItem {
        @Id public Long id;
        public String name;
        public String icon;
        @Column(name = "group_name") public String group;
        @Column(name = "sort_order") public int sortOrder;
    }

    @Entity
    @Table(name = "categories")
    public static class Category {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @Column(name = "user_id") public Long userId;
        public String name;
        @Enumerated(EnumType.STRING) public Kind kind;
        @Column(name = "group_name") public String group;
        public String icon;
        public String color;
        @Column(name = "sort_order") public int sortOrder;
        public boolean hidden;
        @Column(name = "goal_cents") public Long goalCents;
        @Column(name = "created_at") public Instant createdAt;
    }

    @Entity
    @Table(name = "recurrences")
    public static class Recurrence {
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
        @Column(name = "fixed_bill") public boolean fixedBill;
        @Column(name = "created_at") public Instant createdAt;
    }

    @Entity
    @Table(name = "income_sources")
    public static class IncomeSource {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @Column(name = "user_id") public Long userId;
        public String name;
        @Column(name = "amount_cents") public long amountCents;
        public boolean fixed;
        @Column(name = "category_id") public Long categoryId;
        @Column(name = "recurrence_id") public Long recurrenceId;
        @Column(name = "created_at") public Instant createdAt;
    }

    @Entity
    @Table(name = "installment_plans")
    public static class InstallmentPlan {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @Column(name = "user_id") public Long userId;
        @Column(name = "category_id") public Long categoryId;
        public String description;
        @Column(name = "total_cents") public long totalCents;
        @Column(name = "installment_count") public int installmentCount;
        @Column(name = "first_date") public LocalDate firstDate;
        @Enumerated(EnumType.STRING) @Column(name = "payment_method") public PaymentMethod paymentMethod;
        @Column(name = "created_at") public Instant createdAt;
    }

    @Entity
    @Table(name = "import_batches")
    public static class ImportBatch {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @Column(name = "user_id") public Long userId;
        @Column(name = "file_name") public String fileName;
        @Column(name = "row_count") public int rowCount;
        @Column(name = "created_at") public Instant createdAt;
        @Column(name = "undone_at") public Instant undoneAt;
    }

    @Entity(name = "Tx")
    @Table(name = "transactions")
    public static class Transaction {
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
        @Column(name = "deleted_at") public Instant deletedAt;
        @Column(name = "created_at") public Instant createdAt;

        public Transaction copy() {
            Transaction t = new Transaction();
            t.userId = userId; t.kind = kind; t.date = date; t.description = description;
            t.categoryId = categoryId; t.amountCents = amountCents; t.paymentMethod = paymentMethod;
            t.status = status; t.installmentNumber = installmentNumber; t.installmentTotal = installmentTotal;
            t.recurrenceId = recurrenceId; t.installmentPlanId = installmentPlanId; t.origin = origin;
            t.importBatchId = importBatchId; t.note = note; t.createdAt = Instant.now();
            return t;
        }
    }

    @Entity
    @Table(name = "magic_link_tokens")
    public static class MagicLinkToken {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @Column(name = "user_id") public Long userId;
        @Column(name = "token_hash") public String tokenHash;
        @Column(name = "expires_at") public Instant expiresAt;
        @Column(name = "used_at") public Instant usedAt;
    }
}
