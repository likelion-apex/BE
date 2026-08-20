package domain.inventory.dto.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * 성분의 피부 효능 태그(피부 보습/피부 보호). 성분 하나당 0~2개까지 선택 가능하며,
 * 해당 사항이 없으면 빈 배열도 허용된다(강제 기본값 없음). {@link #fromRaw(String)}은
 * 매칭되지 않으면 null을 반환하며, 호출측에서 null은 걸러낸다.
 */
public enum SkinEfficacyTag {

    MOISTURIZING("피부 보습"),
    PROTECTION("피부 보호");

    private final String label;

    SkinEfficacyTag(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static SkinEfficacyTag fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = normalize(raw);
        return Arrays.stream(values())
                .filter(tag -> tag.name().equalsIgnoreCase(normalized) || normalize(tag.label).equals(normalized))
                .findFirst()
                .orElseGet(() -> looseMatch(normalized));
    }

    private static SkinEfficacyTag looseMatch(String normalized) {
        return Arrays.stream(values())
                .filter(tag -> normalized.contains(normalize(tag.label)) || normalize(tag.label).contains(normalized))
                .findFirst()
                .orElse(null);
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", "");
    }
}
