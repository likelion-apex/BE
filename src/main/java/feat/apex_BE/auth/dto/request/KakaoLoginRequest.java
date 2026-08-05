package feat.apex_BE.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "카카오 로그인 요청")
public record KakaoLoginRequest(

        @Schema(description = "프론트엔드가 카카오 인가 서버로부터 전달받은 authorization code", example = "abcd1234...")
        @NotBlank(message = "인가 코드(code)는 필수입니다.")
        String code
) {
}
