package domain.beauty.shortform.client;

import java.math.BigInteger;
import java.time.Duration;

public record YouTubeVideoMetadata(
        String videoId,
        Duration duration,
        String title,
        String thumbnailUrl,
        String publisher,
        BigInteger viewCount
) {
}
