package domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "심사용 ID/PW 로그인 요청")
public record LocalLoginRequest(
        @NotBlank(message = "로그인 ID는 필수입니다.")
        @Size(max = 50, message = "로그인 ID는 50자 이하여야 합니다.")
        @Schema(description = "사전 발급된 로그인 ID", example = "soak_judge")
        String loginId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(max = 100, message = "비밀번호는 100자 이하여야 합니다.")
        @Schema(description = "사전 발급된 비밀번호", format = "password")
        String password
) {
}
