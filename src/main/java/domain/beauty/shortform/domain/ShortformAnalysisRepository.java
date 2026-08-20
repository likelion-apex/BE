package domain.beauty.shortform.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortformAnalysisRepository extends JpaRepository<ShortformAnalysis, Long> {

    Optional<ShortformAnalysis> findByIdAndMemberId(Long id, Long memberId);

    Optional<ShortformAnalysis> findFirstByMemberIdAndAnalysisFingerprintAndStatusOrderByCreatedAtDesc(
            Long memberId,
            String analysisFingerprint,
            ShortformAnalysisStatus status
    );

    Optional<ShortformAnalysis> findFirstByMemberIdAndAnalysisFingerprintOrderByCreatedAtDesc(
            Long memberId,
            String analysisFingerprint
    );

    @Query("""
            select analysis.id as analysisId,
                   analysis.status as status,
                   analysis.videoId as videoId,
                   analysis.thumbnailUrl as thumbnailUrl,
                   analysis.resultTitle as title,
                   analysis.resultStepCount as stepCount,
                   analysis.resultOverallScore as overallScore,
                   analysis.createdAt as createdAt
            from ShortformAnalysis analysis
            where analysis.member.id = :memberId
              and analysis.status not in :excludedStatuses
            order by analysis.createdAt desc
            """)
    List<HistorySummary> findRecentSummaries(
            @Param("memberId") Long memberId,
            @Param("excludedStatuses") List<ShortformAnalysisStatus> excludedStatuses,
            Pageable pageable
    );

    interface HistorySummary {
        Long getAnalysisId();

        ShortformAnalysisStatus getStatus();

        String getVideoId();

        String getThumbnailUrl();

        String getTitle();

        Integer getStepCount();

        Integer getOverallScore();

        LocalDateTime getCreatedAt();
    }
}
