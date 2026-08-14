package domain.beauty.shortform.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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

    List<ShortformAnalysis> findTop10ByMemberIdOrderByCreatedAtDesc(Long memberId);
}
