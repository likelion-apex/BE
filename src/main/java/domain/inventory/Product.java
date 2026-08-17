package domain.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 화장품 마스터 카탈로그. 사용자별 데이터가 아니라 전체 사용자가 공유하는 상품 정보이다.
 * 인벤토리에 처음 추가될 때 존재하지 않으면 새로 생성되며(카테고리는 AI가 자동 분류),
 * 이후 검색·다른 사용자의 인벤토리 추가에서 재사용된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;

    @Column
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProductCategory category;

    @Column(name = "image_url")
    private String imageUrl;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    public Product(String name, String brand, ProductCategory category, String imageUrl) {
        this.name = name;
        this.normalizedName = ProductNameNormalizer.canonicalKey(name);
        this.brand = brand;
        this.category = category;
        this.imageUrl = imageUrl;
    }

    public void update(String brand, ProductCategory category, String imageUrl) {
        this.brand = brand;
        this.category = category;
        this.imageUrl = imageUrl;
    }
}
