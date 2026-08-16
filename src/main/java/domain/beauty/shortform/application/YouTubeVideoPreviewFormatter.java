package domain.beauty.shortform.application;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.Locale;

final class YouTubeVideoPreviewFormatter {

    private static final BigInteger TEN_THOUSAND = BigInteger.valueOf(10_000);
    private static final BigInteger ONE_HUNDRED_MILLION = BigInteger.valueOf(100_000_000);

    private YouTubeVideoPreviewFormatter() {
    }

    static String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return "%d:%02d".formatted(minutes, seconds);
    }

    static String formatViewCount(BigInteger viewCount) {
        if (viewCount == null) {
            return "조회수 비공개";
        }
        if (viewCount.compareTo(TEN_THOUSAND) < 0) {
            return NumberFormat.getIntegerInstance(Locale.KOREA).format(viewCount) + "회";
        }
        if (viewCount.compareTo(ONE_HUNDRED_MILLION) < 0) {
            return compact(viewCount, TEN_THOUSAND) + "만회";
        }
        return compact(viewCount, ONE_HUNDRED_MILLION) + "억회";
    }

    private static String compact(BigInteger value, BigInteger unit) {
        BigDecimal compact = new BigDecimal(value)
                .divide(new BigDecimal(unit), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return compact.toPlainString();
    }
}
