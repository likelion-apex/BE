package domain.beauty.shortform.client;

import java.time.Duration;

public record YouTubeVideoMetadata(String videoId, Duration duration, String title, String thumbnailUrl) {
}
