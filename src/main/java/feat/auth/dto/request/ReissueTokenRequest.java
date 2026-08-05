package feat.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Access/Refresh ?? ??? ??")
public record ReissueTokenRequest(

        @Schema(description = "????? refresh token")
        @NotBlank(message = "refreshToken? ?????.")
        String refreshToken
) {
}
