package br.com.fynavero.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Cor que a pessoa escolheu para uma etiqueta (token da paleta, ex.: "chart-7"). Ver {@link Tags}. */
@Entity
@Table(name = "tag_colors")
public class TagColor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id") public Long userId;
    public String tag;
    public String color;

    public interface TagColorRepository extends JpaRepository<TagColor, Long> {
        List<TagColor> findByUserId(Long userId);

        Optional<TagColor> findByUserIdAndTag(Long userId, String tag);

        void deleteByUserId(Long userId);
    }
}
