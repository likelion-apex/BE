package domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "카카오 로그인 요청")
public record KakaoLoginRequest(

        @Schema(description = "카카오에서 로그인 인가 후 리다이렉트로 전달받은 authorization code", example = "abcd1234...")
        @NotBlank(message = "인가 코드(code)는 필수입니다.")
        String code
) {
}
