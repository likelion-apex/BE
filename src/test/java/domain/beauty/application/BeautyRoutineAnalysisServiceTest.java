package domain.beauty.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Caffeine;
import domain.beauty.client.BeautyRoutineGateway;
import domain.beauty.config.GeminiProperties;
import domain.beauty.domain.BeautyRoutineAnalysis;
import domain.beauty.domain.BeautyRoutineAnalysis.AnalysisStatus;
import domain.beauty.domain.BeautyRoutineAnalysis.RoutineType;
import domain.beauty.domain.BeautyRoutineAnalysisResult;
import domain.beauty.domain.BeautyRoutineAnalysisResult.TokenUsage;
import domain.beauty.support.YouTubeUrlNormalizer;
import org.junit.jupiter.api.Test;

class BeautyRoutineAnalysisServiceTest {

	@Test
	void reusesCachedAnalysisAcrossEquivalentYouTubeUrls() {
		AtomicInteger calls = new AtomicInteger();
		BeautyRoutineGateway gateway = video -> {
			calls.incrementAndGet();
			return new BeautyRoutineAnalysisResult(
				video.videoId(),
				video.watchUrl(),
				"gemini-3.5-flash",
				new TokenUsage(10, 5, 0, 15),
				new BeautyRoutineAnalysis(
					"1.0",
					AnalysisStatus.COMPLETE,
					RoutineType.SKINCARE,
					"스킨케어 루틴입니다.",
					List.of(),
					List.of()
				)
			);
		};
		GeminiProperties properties = new GeminiProperties();
		BeautyRoutineAnalysisService service = new BeautyRoutineAnalysisService(
			new YouTubeUrlNormalizer(),
			gateway,
			properties,
			Caffeine.newBuilder().expireAfterWrite(Duration.ofDays(30)).build()
		);

		BeautyRoutineAnalysisResult first = service.analyze("https://www.youtube.com/shorts/-PC1SkLxtvo");
		BeautyRoutineAnalysisResult second = service.analyze("https://youtu.be/-PC1SkLxtvo");

		assertThat(second).isSameAs(first);
		assertThat(calls).hasValue(1);
	}
}
