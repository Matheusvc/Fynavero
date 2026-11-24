package br.com.fynavero.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InstallmentPlanRepository extends JpaRepository<InstallmentPlan, Long> {
    List<InstallmentPlan> findByUserIdOrderByFirstDateDesc(Long userId);

    Optional<InstallmentPlan> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}
