package br.com.fynavero.category;

import br.com.fynavero.ledger.Kind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByUserIdOrderByKindAscSortOrderAsc(Long userId);

    List<Category> findByUserIdAndKindOrderBySortOrder(Long userId, Kind kind);

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndKind(Long userId, Kind kind);

    void deleteByUserId(Long userId);
}
