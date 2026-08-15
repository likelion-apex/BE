package domain.beauty.shortform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "shortform_product_enrichments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_shortform_product_enrichment_cache_key", columnNames = "cache_key")
})
public class ShortformProductEnrichment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cache_key", nullable = false, length = 64)
    private String cacheKey;

    @Column(nullable = false, length = 80)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 40)
    private String promptVersion;

    @Lob
    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ShortformProductEnrichment(
            String cacheKey,
            String model,
            String promptVersion,
            String resultJson,
            long inputTokens,
            long outputTokens,
            LocalDateTime expiresAt
    ) {
        this.cacheKey = cacheKey;
        this.model = model;
        this.promptVersion = promptVersion;
        this.resultJson = resultJson;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public void refresh(
            String model,
            String promptVersion,
            String resultJson,
            long inputTokens,
            long outputTokens,
            LocalDateTime expiresAt
    ) {
        this.model = model;
        this.promptVersion = promptVersion;
        this.resultJson = resultJson;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.expiresAt = expiresAt;
    }
}
