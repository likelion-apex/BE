package domain.inventory;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    List<Inventory> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<Inventory> findAllByMemberIdAndFavoriteTrueOrderByCreatedAtDesc(Long memberId, Pageable pageable);

    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    long countByMemberIdAndFavoriteTrue(Long memberId);

    Optional<Inventory> findByIdAndMemberId(Long id, Long memberId);
}
