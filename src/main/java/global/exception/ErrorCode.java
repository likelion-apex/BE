package global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON-400", "잘못된 입력값입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON-405", "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON-415", "지원하지 않는 미디어 타입입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-500", "서버 내부 오류입니다."),

    // 인증 (카카오 소셜로그인 / JWT)
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-001", "유효하지 않거나 만료된 토큰입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH-002", "접근 권한이 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH-003", "사용자를 찾을 수 없습니다."),
    KAKAO_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH-004", "카카오 로그인에 실패했습니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-005", "만료된 토큰입니다."),
    UNSUPPORTED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-006", "지원하지 않는 토큰 형식입니다."),
    MISSING_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-007", "필요한 토큰이 존재하지 않습니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH-008", "리프레시 토큰을 찾을 수 없습니다. 다시 로그인해주세요."),
    KAKAO_TOKEN_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "AUTH-009", "카카오 토큰 발급 요청에 실패했습니다."),
    KAKAO_USER_INFO_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "AUTH-010", "카카오 사용자 정보 조회에 실패했습니다."),

    // 회원
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER-001", "회원 정보를 찾을 수 없습니다."),

    // YouTube 뷰티 루틴 분석
    BEAUTY_INVALID_YOUTUBE_URL(HttpStatus.BAD_REQUEST, "BEAUTY-001", "유효한 YouTube URL이 아닙니다."),
    BEAUTY_GEMINI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "BEAUTY-002", "Gemini 서비스를 일시적으로 사용할 수 없습니다."),
    BEAUTY_INVALID_GEMINI_RESPONSE(HttpStatus.BAD_GATEWAY, "BEAUTY-003", "Gemini 분석 응답을 처리할 수 없습니다."),
    BEAUTY_MISSING_GEMINI_CONFIGURATION(HttpStatus.SERVICE_UNAVAILABLE, "BEAUTY-004", "Gemini 서버 설정이 필요합니다."),

    // AI 전체 스킨케어 루틴 분석
    SHORTFORM_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "ANALYSIS-001", "루틴 분석 결과를 찾을 수 없습니다."),
    SHORTFORM_ANALYSIS_NOT_COMPLETED(HttpStatus.CONFLICT, "ANALYSIS-002", "루틴 분석이 아직 완료되지 않았습니다."),
    SHORTFORM_VIDEO_TOO_LONG(HttpStatus.BAD_REQUEST, "ANALYSIS-003", "최대 5분 이하의 영상만 분석할 수 있습니다."),
    SHORTFORM_VIDEO_UNAVAILABLE(HttpStatus.BAD_REQUEST, "ANALYSIS-004", "공개 상태의 YouTube 영상을 확인할 수 없습니다."),
    SHORTFORM_PROFILE_INCOMPLETE(HttpStatus.BAD_REQUEST, "ANALYSIS-005", "피부 타입을 먼저 등록해 주세요."),
    SHORTFORM_CONFIGURATION_MISSING(HttpStatus.SERVICE_UNAVAILABLE, "ANALYSIS-006", "루틴 분석 서버 설정이 필요합니다."),
    SHORTFORM_EXTERNAL_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "ANALYSIS-007", "외부 분석 서비스를 일시적으로 사용할 수 없습니다."),
    SHORTFORM_INVALID_AI_RESPONSE(HttpStatus.BAD_GATEWAY, "ANALYSIS-008", "AI 분석 응답을 처리할 수 없습니다."),
    SHORTFORM_ANALYSIS_CANCELLED(HttpStatus.CONFLICT, "ANALYSIS-009", "취소된 루틴 분석입니다."),
    SHORTFORM_OPTIMIZATION_REQUIRED(HttpStatus.CONFLICT, "ANALYSIS-010", "인벤토리 최적화를 먼저 확인해 주세요."),
    SHORTFORM_NOT_SKINCARE(HttpStatus.BAD_REQUEST, "ANALYSIS-011", "스킨케어 루틴 영상만 분석할 수 있습니다."),
    ROUTINE_TODAY_CONFLICT(HttpStatus.CONFLICT, "ROUTINE-001", "이미 진행 중인 오늘의 루틴이 있습니다."),
    ROUTINE_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUTINE-002", "오늘의 루틴 기록을 찾을 수 없습니다."),
    ROUTINE_LOG_STEP_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUTINE-003", "루틴 스텝을 찾을 수 없습니다."),
    ROUTINE_LOG_STEPS_INCOMPLETE(HttpStatus.CONFLICT, "ROUTINE-004", "아직 완료하지 않은 스텝이 있습니다."),

    // 인벤토리
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "INVENTORY-001", "화장품 정보를 찾을 수 없습니다."),
    INVENTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "INVENTORY-002", "인벤토리 항목을 찾을 수 없습니다."),
    AI_ANALYSIS_FAILED(HttpStatus.BAD_GATEWAY, "INVENTORY-003", "AI 분석에 실패했습니다. 잠시 후 다시 시도해주세요."),

    // 성분 궁합 분석
    INGREDIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "INGREDIENT-001", "성분 정보를 찾을 수 없습니다.");

    // 각자 도메인 에러는 여기 이어서 추가 ~

    private final HttpStatus status;
    private final String code;
    private final String message;

}
