package domain.routine.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.util.Arrays;

public enum DailyConditionType {

    TROUBLED("트러블있고예민해요"),
    DRY("건조하고푸석해요"),
    NORMAL("평범하고무난해요"),
    MOIST("촉촉하고편안해요"),
    BEST("컨디션최고예요");

    private final String label;

    DailyConditionType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static DailyConditionType from(String label) {
        return Arrays.stream(values())
                .filter(value -> value.label.equals(label))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 컨디션입니다: " + label));
    }
}