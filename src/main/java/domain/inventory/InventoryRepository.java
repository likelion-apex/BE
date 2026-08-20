package domain.inventory;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    List<Inventory> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<Inventory> findAllByMemberIdAndFavoriteTrueOrderByCreatedAtDesc(Long memberId, Pageable pageable);

    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    boolean existsByMemberId(Long memberId);

    long countByMemberIdAndFavoriteTrue(Long memberId);

    Optional<Inventory> findByIdAndMemberId(Long id, Long memberId);

    @Query("SELECT i.product.id FROM Inventory i GROUP BY i.product.id ORDER BY COUNT(i) DESC")
    List<Long> findTopProductIdsByInventoryCount(Pageable pageable);
}
