package domain.beauty.application;

import com.github.benmanes.caffeine.cache.Cache;
import domain.beauty.client.BeautyRoutineGateway;
import domain.beauty.client.GeminiPromptResources;
import domain.beauty.config.GeminiProperties;
import domain.beauty.domain.BeautyRoutineAnalysisResult;
import domain.beauty.domain.NormalizedYouTubeVideo;
import domain.beauty.support.YouTubeUrlNormalizer;
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
