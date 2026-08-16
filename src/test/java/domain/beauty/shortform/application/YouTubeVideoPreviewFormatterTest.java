package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class YouTubeVideoPreviewFormatterTest {

    @Test
    void formatsDurationAsMinutesAndTwoDigitSeconds() {
        assertThat(YouTubeVideoPreviewFormatter.formatDuration(Duration.ofSeconds(58))).isEqualTo("0:58");
        assertThat(YouTubeVideoPreviewFormatter.formatDuration(Duration.ofSeconds(300))).isEqualTo("5:00");
    }

    @Test
    void formatsViewCountForKoreanCompactDisplay() {
        assertThat(YouTubeVideoPreviewFormatter.formatViewCount(BigInteger.valueOf(9_999))).isEqualTo("9,999회");
        assertThat(YouTubeVideoPreviewFormatter.formatViewCount(BigInteger.valueOf(10_000))).isEqualTo("1만회");
        assertThat(YouTubeVideoPreviewFormatter.formatViewCount(BigInteger.valueOf(123_456))).isEqualTo("12.3만회");
        assertThat(YouTubeVideoPreviewFormatter.formatViewCount(BigInteger.valueOf(123_456_789))).isEqualTo("1.2억회");
        assertThat(YouTubeVideoPreviewFormatter.formatViewCount(null)).isEqualTo("조회수 비공개");
    }
}
