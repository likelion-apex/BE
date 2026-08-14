package domain.beauty.shortform.application;

import domain.beauty.domain.BeautyRoutineAnalysis.Step;

public record MatchedVideoStep(Step source, Long productId, String imageUrl) {
}
