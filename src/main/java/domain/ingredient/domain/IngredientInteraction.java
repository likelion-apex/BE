package domain.ingredient.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 성분 궁합 로직 DB
 * 저장 규칙: ingredientAId < ingredientBId 로 항상 작은 ID가 A에 오도록 통일 (중복 저장 방지)
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "ingredient_interactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ingredient_pair",
                        columnNames = {"ingredient_a_id", "ingredient_b_id"}
                )
        }
)
public class IngredientInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interaction_id")
    private Long id;

    @Column(name = "ingredient_a_id", nullable = false)
    private Long ingredientAId;

    @Column(name = "ingredient_b_id", nullable = false)
    private Long ingredientBId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false)
    private InteractionType interactionType;

    @Column(name = "description")
    private String description;

    @Builder
    private IngredientInteraction(Long ingredientAId, Long ingredientBId, InteractionType interactionType, String description) {
        this.ingredientAId = ingredientAId;
        this.ingredientBId = ingredientBId;
        this.interactionType = interactionType;
        this.description = description;
    }
}
