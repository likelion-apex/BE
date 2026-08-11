package feat.domain.member;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import feat.common.exception.CustomException;
import feat.common.exception.ErrorCode;
import java.util.Arrays;

public enum SkinConcern {

    DRYNESS("속건조"),
    ACNE("여드름"),
    SENSITIVE("민감성"),
    WHITENING("미백·잡티"),
    DARK_CIRCLE("다크서클"),
    PIGMENTATION("색소·블랙헤드"),
    REDNESS("홍조"),
    ATOPIC("아토피");

    private final String label;

    SkinConcern(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static SkinConcern from(String label) {
        return Arrays.stream(values())
                .filter(value -> value.label.equals(label))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 피부고민입니다: " + label));
    }
}
