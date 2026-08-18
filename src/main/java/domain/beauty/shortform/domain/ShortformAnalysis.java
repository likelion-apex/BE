package domain.beauty.shortform.domain;

import domain.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "shortform_analyses")
public class ShortformAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extraction_id")
    private VideoRoutineExtraction extraction;

    @Column(name = "video_id", nullable = false, length = 20)
    private String videoId;

    @Column(name = "youtube_url", nullable = false, length = 500)
    private String youtubeUrl;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(name = "analysis_fingerprint", nullable = false, length = 64)
    private String analysisFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShortformAnalysisStatus status;

    @Column(nullable = false)
    private int progress;

    @Column(name = "status_message", length = 300)
    private String statusMessage;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Lob
    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "result_title", length = 100)
    private String resultTitle;

    @Column(name = "result_step_count")
    private Integer resultStepCount;

    @Column(name = "result_overall_score")
    private Integer resultOverallScore;

    @Lob
    @Column(name = "optimization_json", columnDefinition = "TEXT")
    private String optimizationJson;

    @Column(name = "optimization_reason_version", length = 20)
    private String optimizationReasonVersion;

    @Column(name = "openai_model", length = 80)
    private String openAiModel;

    @Column(name = "openai_prompt_version", length = 40)
    private String openAiPromptVersion;

    @Column(name = "openai_input_tokens")
    private Long openAiInputTokens;

    @Column(name = "openai_output_tokens")
    private Long openAiOutputTokens;

    @Column(name = "optimized_at")
    private LocalDateTime optimizedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public ShortformAnalysis(
            Member member,
            String videoId,
            String youtubeUrl,
            String analysisFingerprint
    ) {
        this.member = member;
        this.videoId = videoId;
        this.youtubeUrl = youtubeUrl;
        this.analysisFingerprint = analysisFingerprint;
        this.status = ShortformAnalysisStatus.PENDING;
        this.progress = 0;
        this.statusMessage = "분석 요청을 준비하고 있습니다.";
    }

    public void cacheThumbnailUrl(String thumbnailUrl) {
        if ((this.thumbnailUrl == null || this.thumbnailUrl.isBlank())
                && thumbnailUrl != null
                && !thumbnailUrl.isBlank()) {
            this.thumbnailUrl = thumbnailUrl.trim();
        }
    }

    public void moveTo(ShortformAnalysisStatus nextStatus, String message) {
        if (status.isTerminal()) {
            return;
        }
        this.status = nextStatus;
        this.progress = nextStatus.defaultProgress();
        this.statusMessage = message;
    }

    public void attachExtraction(VideoRoutineExtraction extraction) {
        this.extraction = extraction;
    }

    public void complete(
            VideoRoutineExtraction extraction,
            String resultJson,
            String optimizationJson,
            String resultTitle,
            int resultStepCount,
            int resultOverallScore,
            String openAiModel,
            String openAiPromptVersion,
            long inputTokens,
            long outputTokens
    ) {
        if (status == ShortformAnalysisStatus.CANCELLED) {
            return;
        }
        this.extraction = extraction;
        this.resultJson = resultJson;
        this.optimizationJson = optimizationJson;
        this.resultTitle = resultTitle;
        this.resultStepCount = resultStepCount;
        this.resultOverallScore = resultOverallScore;
        this.openAiModel = openAiModel;
        this.openAiPromptVersion = openAiPromptVersion;
        this.optimizationReasonVersion = openAiPromptVersion;
        this.openAiInputTokens = inputTokens;
        this.openAiOutputTokens = outputTokens;
        this.status = ShortformAnalysisStatus.COMPLETED;
        this.progress = 100;
        this.statusMessage = "루틴 분석이 완료되었습니다.";
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String errorCode, String errorMessage) {
        if (status == ShortformAnalysisStatus.CANCELLED) {
            return;
        }
        this.status = ShortformAnalysisStatus.FAILED;
        this.progress = 100;
        this.statusMessage = "루틴 분석에 실패했습니다.";
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (status.isTerminal()) {
            return;
        }
        this.status = ShortformAnalysisStatus.CANCELLED;
        this.progress = 100;
        this.statusMessage = "사용자가 분석을 취소했습니다.";
        this.completedAt = LocalDateTime.now();
    }

    public void markOptimized() {
        if (status == ShortformAnalysisStatus.COMPLETED && optimizedAt == null) {
            this.optimizedAt = LocalDateTime.now();
        }
    }

    public void replaceOptimization(String optimizationJson) {
        this.optimizationJson = optimizationJson;
    }

    public void replaceOptimization(String optimizationJson, String reasonVersion) {
        this.optimizationJson = optimizationJson;
        this.optimizationReasonVersion = reasonVersion;
    }
}
