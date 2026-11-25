package br.com.fynavero.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Fonte de renda. Com valor informado, gera uma recorrência mensal de receita (estimada, se variável). */
@Entity
@Table(name = "income_sources")
public class IncomeSource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id") public Long userId;
    public String name;
    @Column(name = "amount_cents") public long amountCents;
    /** Fixa (mesmo valor todo mês) ou variável (valor estimado). */
    public boolean fixed;
    @Column(name = "category_id") public Long categoryId;
    @Column(name = "recurrence_id") public Long recurrenceId;
    @Column(name = "created_at") public Instant createdAt;

    public interface IncomeSourceRepository extends JpaRepository<IncomeSource, Long> {
        List<IncomeSource> findByUserIdOrderByCreatedAt(Long userId);
    
        Optional<IncomeSource> findByIdAndUserId(Long id, Long userId);
    
        void deleteByUserId(Long userId);
    }
}
