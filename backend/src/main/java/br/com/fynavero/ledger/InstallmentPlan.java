package br.com.fynavero.ledger;

import br.com.fynavero.ledger.Transaction.PaymentMethod;
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
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Compra parcelada ou dívida: agrupa as parcelas (lançamentos com installmentPlanId). */
@Entity
@Table(name = "installment_plans")
public class InstallmentPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id") public Long userId;
    @Column(name = "category_id") public Long categoryId;
    public String description;
    @Column(name = "total_cents") public long totalCents;
    @Column(name = "installment_count") public int installmentCount;
    @Column(name = "first_date") public LocalDate firstDate;
    @Enumerated(EnumType.STRING) @Column(name = "payment_method") public PaymentMethod paymentMethod;
    @Column(name = "created_at") public Instant createdAt;

    public interface InstallmentPlanRepository extends JpaRepository<InstallmentPlan, Long> {
        List<InstallmentPlan> findByUserIdOrderByFirstDateDesc(Long userId);
    
        Optional<InstallmentPlan> findByIdAndUserId(Long id, Long userId);
    
        void deleteByUserId(Long userId);
    }
}
