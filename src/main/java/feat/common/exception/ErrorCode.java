package feat.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_001", "??? ?????."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_002", "?? ?? ??? ??????."),

    // Auth / JWT
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_001", "???? ?? ?????."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_002", "??? ?????."),
    UNSUPPORTED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_003", "???? ?? ?? ?????."),
    MISSING_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_004", "?? ??? ???? ????."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH_005", "???? ??? ?? ? ????. ?? ???????."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_006", "?? ??? ????."),

    // Kakao OAuth2
    KAKAO_TOKEN_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "KAKAO_001", "??? ?? ??? ??????."),
    KAKAO_USER_INFO_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "KAKAO_002", "??? ??? ?? ??? ??????."),

    // Member
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_001", "???? ?? ?????.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
