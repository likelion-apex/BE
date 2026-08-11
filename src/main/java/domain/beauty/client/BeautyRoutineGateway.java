package domain.beauty.client;

import domain.beauty.domain.BeautyRoutineAnalysisResult;
import domain.beauty.domain.NormalizedYouTubeVideo;

public interface BeautyRoutineGateway {

	BeautyRoutineAnalysisResult analyze(NormalizedYouTubeVideo video);
}
