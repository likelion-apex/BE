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
@Table(name = "video_routine_extractions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_video_routine_extraction_cache_key", columnNames = "cache_key")
})
public class VideoRoutineExtraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cache_key", nullable = false, length = 180)
    private String cacheKey;

    @Column(name = "video_id", nullable = false, length = 20)
    private String videoId;

    @Column(name = "youtube_url", nullable = false, length = 500)
    private String youtubeUrl;

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public VideoRoutineExtraction(
            String cacheKey,
            String videoId,
            String youtubeUrl,
            String model,
            String promptVersion,
            String resultJson,
            long inputTokens,
            long outputTokens
    ) {
        this.cacheKey = cacheKey;
        this.videoId = videoId;
        this.youtubeUrl = youtubeUrl;
        this.model = model;
        this.promptVersion = promptVersion;
        this.resultJson = resultJson;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }
}
