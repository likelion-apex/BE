package domain.inventory.dto.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/**
 * 성분 하나에 대한 AI 판단 위험도. AI가 비어있거나 알 수 없는 값을 반환해도
 * {@link #fromRaw(String)}은 항상 값을 반환한다(예외 없음, 기본값 MEDIUM).
 */
public enum IngredientRiskLevel {

    LOW("낮음"),
    MEDIUM("중간"),
    HIGH("높음");

    private final String label;

    IngredientRiskLevel(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static IngredientRiskLevel fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEDIUM;
        }
        String normalized = raw.trim();
        return Arrays.stream(values())
                .filter(level -> level.name().equalsIgnoreCase(normalized) || level.label.equals(normalized))
                .findFirst()
                .orElseGet(() -> fromLooseMatch(normalized.toUpperCase(Locale.ROOT)));
    }

    private static IngredientRiskLevel fromLooseMatch(String upper) {
        if (upper.contains("HIGH") || upper.contains("위험")) {
            return HIGH;
        }
        if (upper.contains("LOW") || upper.contains("안전")) {
            return LOW;
        }
        return MEDIUM;
    }
}
