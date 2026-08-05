package feat.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "??? ??? ??")
public record KakaoLoginRequest(

        @Schema(description = "?????? ??? ?? ????? ???? authorization code", example = "abcd1234...")
        @NotBlank(message = "?? ??(code)? ?????.")
        String code
) {
}
