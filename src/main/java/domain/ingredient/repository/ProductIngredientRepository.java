package domain.ingredient.repository;

import domain.ingredient.domain.ProductIngredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductIngredientRepository extends JpaRepository<ProductIngredient, Long> {

    // 특정 제품의 전성분 리스트 순위(rank) 순으로 조회
    List<ProductIngredient> findByProduct_IdOrderByRankAsc(Long productId);

    // 여러 제품의 전성분 리스트 한 번에 조회
    List<ProductIngredient> findByProduct_IdIn(List<Long> productIds);
}
