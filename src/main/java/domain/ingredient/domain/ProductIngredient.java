package domain.ingredient.domain;

import domain.inventory.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/**
 * 특정 제품에 포함된 성분 다대다 매핑 테이블
 * 전성분 표기 순서 정보도 함께 관리
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "product_ingredients",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_product_ingredient", columnNames = {"product_id", "ingredient_id"}) // 동일 제품에 동일 성분이 중복 저장되는 것을 방지
        }
)
public class ProductIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_ingredient_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    /** 전성분 표기 순위 (예: 1위 정제수) */
    @Column(name = "ingredient_rank")
    private Integer rank;

    @Builder
    private ProductIngredient(Product product, Ingredient ingredient, Integer rank) {
        this.product = product;
        this.ingredient = ingredient;
        this.rank = rank;
    }
}
