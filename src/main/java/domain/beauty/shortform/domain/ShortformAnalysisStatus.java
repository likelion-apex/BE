package domain.beauty.shortform.domain;

public enum ShortformAnalysisStatus {
    PENDING(0),
    EXTRACTING_VIDEO(20),
    MATCHING_PRODUCTS(45),
    PERSONALIZING(70),
    OPTIMIZING(90),
    COMPLETED(100),
    FAILED(100),
    CANCELLED(100);

    private final int defaultProgress;

    ShortformAnalysisStatus(int defaultProgress) {
        this.defaultProgress = defaultProgress;
    }

    public int defaultProgress() {
        return defaultProgress;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
