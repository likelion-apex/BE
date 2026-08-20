package domain.beauty.shortform.application;

import domain.inventory.ProductCategory;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class KoreanUserCopyNormalizer {

    private static final Pattern HANGUL = Pattern.compile("[가-힣]");
    private static final Pattern LATIN_WORD = Pattern.compile("(?i)\\b[a-z][a-z0-9-]*\\b");
    private static final Set<String> ALLOWED_LATIN_TOKENS = Set.of(
            "ph", "aha", "bha", "pdrn", "ewg", "uv", "spf", "pa", "c");
    private static final List<String> INTERNAL_OPTIMIZATION_COPY = List.of(
            "같은 카테고리", "카테고리가 달라", "추천된 인벤토리", "대체 가능합니다", "서버 보정");

    public boolean isAcceptable(String value) {
        if (value == null || value.isBlank() || !HANGUL.matcher(value).find()) {
            return false;
        }
        int unsupportedLatinWords = 0;
        Matcher matcher = LATIN_WORD.matcher(value);
        while (matcher.find()) {
            String token = matcher.group().toLowerCase(Locale.ROOT);
            if (!ALLOWED_LATIN_TOKENS.contains(token)) {
                unsupportedLatinWords++;
            }
        }
        return unsupportedLatinWords == 0;
    }

    public String normalize(String value, String fallback) {
        String normalized = compact(value);
        return isAcceptable(normalized) ? normalized : fallback;
    }

    public String normalizeOptimizationReason(String value, String fallback) {
        String normalized = compact(value);
        boolean internal = INTERNAL_OPTIMIZATION_COPY.stream().anyMatch(normalized::contains);
        return !internal && isAcceptable(normalized) ? normalized : fallback;
    }

    public List<String> normalizeBenefits(
            List<String> values,
            String rawCategory,
            String productName,
            ShortformProductCategoryResolver categoryResolver
    ) {
        List<String> normalized = safe(values).stream()
                .map(this::compact)
                .filter(this::isAcceptable)
                .map(value -> value.replaceAll("[.!?]+$", ""))
                .distinct()
                .limit(2)
                .toList();
        return normalized.isEmpty()
                ? fallbackBenefits(categoryResolver.resolve(rawCategory, productName), rawCategory, productName)
                : normalized;
    }

    public String optimizationFallback(ProductCategory category, boolean replacement) {
        String role = categoryRole(category);
        return replacement
                ? "보유 제품으로 " + role + " 단계를 이어가며 추가 구매 없이 루틴을 구성할 수 있어요."
                : role + " 역할을 대신할 확인된 보유 제품이 없어 영상 속 제품을 사용해 주세요.";
    }

    private List<String> fallbackBenefits(
            ProductCategory category,
            String rawCategory,
            String productName
    ) {
        if (category != null) {
            return switch (category) {
                case SKIN_TONER, SKIN_TONERPAD -> List.of("피부 결 정돈");
                case LOTION, ESSENCE_SERUM, MIST -> List.of("수분 공급");
                case FACEOIL -> List.of("보습막 형성");
                case CREAM, BAM -> List.of("피부 장벽 보습");
                case EYECARE -> List.of("눈가 보습");
            };
        }
        String source = (compact(rawCategory) + " " + compact(productName)).toLowerCase(Locale.ROOT);
        if (containsAny(source, "클렌", "세안", "워시", "cleans", "wash")) {
            return List.of("노폐물 세정");
        }
        if (containsAny(source, "선크림", "선케어", "자외선", "sunscreen", "suncream")) {
            return List.of("자외선 차단");
        }
        if (containsAny(source, "마스크", "팩", "mask")) {
            return List.of("피부 진정");
        }
        return List.of("피부 컨디션 관리");
    }

    private String categoryRole(ProductCategory category) {
        if (category == null) {
            return "해당 단계의 피부 관리";
        }
        return switch (category) {
            case SKIN_TONER, SKIN_TONERPAD -> "피부 결 정돈";
            case LOTION, ESSENCE_SERUM, MIST -> "수분 공급";
            case FACEOIL -> "보습막 형성";
            case CREAM, BAM -> "피부 장벽 보습";
            case EYECARE -> "눈가 보습";
        };
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
