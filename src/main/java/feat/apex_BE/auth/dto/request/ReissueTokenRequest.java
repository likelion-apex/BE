package feat.apex_BE.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Access/Refresh 토큰 재발급 요청")
public record ReissueTokenRequest(

        @Schema(description = "재발급받을 refresh token")
        @NotBlank(message = "refreshToken은 필수입니다.")
        String refreshToken
) {
}
