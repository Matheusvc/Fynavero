package br.com.fynavero.importing;

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

/** Uma importação de planilha. Desfazer apaga só os lançamentos deste lote. */
@Entity
@Table(name = "import_batches")
public class ImportBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id") public Long userId;
    @Column(name = "file_name") public String fileName;
    @Column(name = "row_count") public int rowCount;
    @Column(name = "created_at") public Instant createdAt;
    @Column(name = "undone_at") public Instant undoneAt;

    public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
        List<ImportBatch> findByUserIdOrderByCreatedAtDesc(Long userId);
    
        Optional<ImportBatch> findByIdAndUserId(Long id, Long userId);
    
        void deleteByUserId(Long userId);
    }
}
