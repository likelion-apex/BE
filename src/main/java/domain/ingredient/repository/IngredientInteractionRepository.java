package domain.ingredient.repository;

import domain.ingredient.domain.IngredientInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IngredientInteractionRepository extends JpaRepository<IngredientInteraction, Long> {

    /**
     * 주어진 성분 ID 집합 안에서 성립하는 궁합 규칙을 전부 가져옴 (N+1 방지)
     */
    @Query("""
        SELECT i FROM IngredientInteraction i
        WHERE i.ingredientAId IN :ingredientIds
          AND i.ingredientBId IN :ingredientIds
    """)
    List<IngredientInteraction> findAllAmong(@Param("ingredientIds") List<Long> ingredientIds);

    /**
     * 특정 두 성분 간의 단일 궁합 조회 (필요시 사용)
     */
    Optional<IngredientInteraction> findByIngredientAIdAndIngredientBId(Long ingredientAId, Long ingredientBId);
}
