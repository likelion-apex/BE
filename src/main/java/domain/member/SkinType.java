package domain.member;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.util.Arrays;

public enum SkinType {

    DRY("건성"),
    NORMAL("중성"),
    OILY("지성"),
    COMBINATION("복합성"),
    DEHYDRATED_OILY("수부지");

    private final String label;

    SkinType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static SkinType from(String label) {
        return Arrays.stream(values())
                .filter(value -> value.label.equals(label))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 피부타입입니다: " + label));
    }
}
