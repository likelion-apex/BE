package domain.inventory.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "inventory_ai_caches")
public class InventoryAiCache {

    @Id
    @Column(name = "cache_key", length = 255)
    private String cacheKey;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public InventoryAiCache(String cacheKey, String payload, LocalDateTime expiresAt) {
        this.cacheKey = cacheKey;
        this.payload = payload;
        this.expiresAt = expiresAt;
    }

    public void refresh(String payload, LocalDateTime expiresAt) {
        this.payload = payload;
        this.expiresAt = expiresAt;
    }

    public boolean expired(LocalDateTime now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }
}
