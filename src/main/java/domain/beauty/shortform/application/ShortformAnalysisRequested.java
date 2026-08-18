package domain.beauty.shortform.application;

public record ShortformAnalysisRequested(
        Long analysisId,
        boolean refreshUnverifiedProducts
) {

    public ShortformAnalysisRequested(Long analysisId) {
        this(analysisId, false);
    }
}
