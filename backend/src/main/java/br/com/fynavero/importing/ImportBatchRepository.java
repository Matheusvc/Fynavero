package br.com.fynavero.importing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    List<ImportBatch> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ImportBatch> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}
