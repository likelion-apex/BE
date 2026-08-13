package domain.beauty.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import domain.beauty.domain.NormalizedYouTubeVideo;
import domain.beauty.exception.BeautyRoutineException.InvalidYouTubeUrl;
import org.junit.jupiter.api.Test;

class YouTubeUrlNormalizerTest {

	private final YouTubeUrlNormalizer normalizer = new YouTubeUrlNormalizer();

	@Test
	void normalizesShortsWatchMobileAndShortUrls() {
		assertNormalized("https://www.youtube.com/shorts/-PC1SkLxtvo", "-PC1SkLxtvo");
		assertNormalized("https://youtube.com/watch?v=-PC1SkLxtvo", "-PC1SkLxtvo");
		assertNormalized("https://m.youtube.com/watch?v=-PC1SkLxtvo", "-PC1SkLxtvo");
		assertNormalized("https://youtu.be/-PC1SkLxtvo", "-PC1SkLxtvo");
	}

	@Test
	void rejectsNonHttpsAndLookalikeHosts() {
		assertThatThrownBy(() -> normalizer.normalize("http://youtube.com/shorts/-PC1SkLxtvo"))
			.isInstanceOf(InvalidYouTubeUrl.class);
		assertThatThrownBy(() -> normalizer.normalize("https://youtube.com.evil.example/shorts/-PC1SkLxtvo"))
			.isInstanceOf(InvalidYouTubeUrl.class);
	}

	@Test
	void rejectsUnsupportedPathsAndInvalidVideoIds() {
		assertThatThrownBy(() -> normalizer.normalize("https://www.youtube.com/playlist?list=abc"))
			.isInstanceOf(InvalidYouTubeUrl.class);
		assertThatThrownBy(() -> normalizer.normalize("https://www.youtube.com/shorts/too-short"))
			.isInstanceOf(InvalidYouTubeUrl.class);
	}

	private void assertNormalized(String input, String videoId) {
		NormalizedYouTubeVideo normalized = normalizer.normalize(input);
		assertThat(normalized.videoId()).isEqualTo(videoId);
		assertThat(normalized.watchUrl()).isEqualTo("https://www.youtube.com/watch?v=" + videoId);
	}
}
