package domain.beauty.support;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import domain.beauty.domain.NormalizedYouTubeVideo;
import domain.beauty.exception.BeautyRoutineException.InvalidYouTubeUrl;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class YouTubeUrlNormalizer {

	private static final Set<String> YOUTUBE_HOSTS = Set.of(
		"youtube.com",
		"www.youtube.com",
		"m.youtube.com",
		"youtu.be"
	);
	private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

	public NormalizedYouTubeVideo normalize(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			throw new InvalidYouTubeUrl("YouTube URL을 입력해 주세요.");
		}

		try {
			URI uri = URI.create(rawUrl.trim());
			if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
				throw new InvalidYouTubeUrl("HTTPS YouTube URL만 사용할 수 있습니다.");
			}

			String host = uri.getHost().toLowerCase(Locale.ROOT);
			if (!YOUTUBE_HOSTS.contains(host)) {
				throw new InvalidYouTubeUrl("YouTube 도메인의 영상 URL만 사용할 수 있습니다.");
			}

			String videoId = extractVideoId(uri, host);
			if (videoId == null || !VIDEO_ID.matcher(videoId).matches()) {
				throw new InvalidYouTubeUrl("유효한 YouTube 영상 ID를 찾을 수 없습니다.");
			}

			return new NormalizedYouTubeVideo(videoId, "https://www.youtube.com/watch?v=" + videoId);
		} catch (IllegalArgumentException exception) {
			throw new InvalidYouTubeUrl("올바른 YouTube URL 형식이 아닙니다.");
		}
	}

	private String extractVideoId(URI uri, String host) {
		String path = uri.getPath() == null ? "" : uri.getPath();
		if ("youtu.be".equals(host)) {
			return firstPathSegment(path);
		}
		if (path.startsWith("/shorts/")) {
			return firstPathSegment(path.substring("/shorts".length()));
		}
		if ("/watch".equals(path)) {
			return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("v");
		}
		return null;
	}

	private String firstPathSegment(String path) {
		return path.replaceFirst("^/+", "").split("/", 2)[0];
	}
}
