package br.com.fynavero.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecurrenceRepository extends JpaRepository<Recurrence, Long> {
    List<Recurrence> findByUserIdOrderByDayOfMonth(Long userId);

    Optional<Recurrence> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}
