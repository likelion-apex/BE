package feat.apex_BE.beauty.application;

import com.github.benmanes.caffeine.cache.Cache;
import feat.apex_BE.beauty.client.BeautyRoutineGateway;
import feat.apex_BE.beauty.client.GeminiPromptResources;
import feat.apex_BE.beauty.config.GeminiProperties;
import feat.apex_BE.beauty.domain.BeautyRoutineAnalysisResult;
import feat.apex_BE.beauty.domain.NormalizedYouTubeVideo;
import feat.apex_BE.beauty.support.YouTubeUrlNormalizer;
import org.springframework.stereotype.Service;

@Service
public class BeautyRoutineAnalysisService {

	private final YouTubeUrlNormalizer urlNormalizer;
	private final BeautyRoutineGateway gateway;
	private final GeminiProperties properties;
	private final Cache<String, BeautyRoutineAnalysisResult> cache;

	public BeautyRoutineAnalysisService(
		YouTubeUrlNormalizer urlNormalizer,
		BeautyRoutineGateway gateway,
		GeminiProperties properties,
		Cache<String, BeautyRoutineAnalysisResult> beautyRoutineAnalysisCache
	) {
		this.urlNormalizer = urlNormalizer;
		this.gateway = gateway;
		this.properties = properties;
		this.cache = beautyRoutineAnalysisCache;
	}

	public BeautyRoutineAnalysisResult analyze(String youtubeUrl) {
		NormalizedYouTubeVideo video = urlNormalizer.normalize(youtubeUrl);
		String cacheKey = String.join(":", properties.getModel(), GeminiPromptResources.VERSION, video.videoId());
		return cache.get(cacheKey, ignored -> gateway.analyze(video));
	}
}
