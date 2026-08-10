package feat.apex_BE.beauty.client;

import feat.apex_BE.beauty.domain.BeautyRoutineAnalysisResult;
import feat.apex_BE.beauty.domain.NormalizedYouTubeVideo;

public interface BeautyRoutineGateway {

	BeautyRoutineAnalysisResult analyze(NormalizedYouTubeVideo video);
}
