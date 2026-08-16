package domain.routine.domain;

import domain.inventory.Inventory;
import domain.inventory.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "routine_steps")
public class RoutineStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "routine_id", nullable = false)
    private Routine routine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id")
    private Inventory inventory;

    @Column(name = "step_order", nullable = false)
    private int order;

    @Column(name = "product_name", nullable = false, length = 180)
    private String productName;

    @Column(length = 120)
    private String brand;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "ai_reason", length = 500)
    private String aiReason;

    public RoutineStep(
            Routine routine,
            Product product,
            Inventory inventory,
            int order,
            String productName,
            String brand,
            String category,
            String imageUrl,
            String aiReason
    ) {
        this.routine = routine;
        this.product = product;
        this.inventory = inventory;
        this.order = order;
        this.productName = productName;
        this.brand = brand;
        this.category = category;
        this.imageUrl = imageUrl;
        this.aiReason = aiReason;
    }
}
