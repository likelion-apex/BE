package domain.inventory.dto.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * 성분의 배합목적을 나타내는 7가지 공식 카테고리(화장품성분사전 분류 기준).
 * AI가 반환한 원본 문자열은 {@link #fromRaw(String)}으로 매칭하며, 어느 카테고리와도
 * 일치하지 않으면 null을 반환한다(호출측에서 걸러내고, 결과가 비면 기본 카테고리로 보정한다).
 */
public enum IngredientPurposeCategory {

    SOLVENT("용제"),
    SKIN_CONDITIONING_AGENT("피부컨디셔닝제"),
    HAIR_CONDITIONING_AGENT("헤어컨디셔닝제"),
    SKIN_PROTECTANT("피부보호제"),
    FRAGRANCE_ADDITIVE("착향제"),
    DENATURANT("변성제"),
    FRAGRANCE("향료");

    private final String label;

    IngredientPurposeCategory(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static IngredientPurposeCategory fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = normalize(raw);
        return Arrays.stream(values())
                .filter(category -> category.name().equalsIgnoreCase(normalized)
                        || normalize(category.label).equals(normalized))
                .findFirst()
                .orElseGet(() -> looseMatch(normalized));
    }

    private static IngredientPurposeCategory looseMatch(String normalized) {
        return Arrays.stream(values())
                .filter(category -> normalized.contains(normalize(category.label))
                        || normalize(category.label).contains(normalized))
                .findFirst()
                .orElse(null);
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", "");
    }
}
