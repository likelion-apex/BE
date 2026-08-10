package feat.apex_BE.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import feat.apex_BE.global.exception.ErrorCode;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;

    private ApiResponse(boolean success, String code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // 성공 응답: 데이터가 있을 때
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "COMMON-200", "요청이 성공했습니다.", data);
    }

    // 성공 응답: 데이터가 없을 때
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(true, "COMMON-200", "요청이 성공했습니다.", null);
    }

    // 성공 응답: 메시지를 직접 지정할 때
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, "COMMON-200", message, data);
    }

    // 실패 응답
    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
    }

    // 실패 응답: 메시지를 직접 지정할 때 (예: 예외에 담긴 상세 메시지를 그대로 노출)
    public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.getCode(), message, null);
    }
}
