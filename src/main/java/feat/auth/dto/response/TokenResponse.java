package feat.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "???/??? ?? ? ???? ?? ??")
public record TokenResponse(
        @Schema(description = "API ??? ???? Access Token") String accessToken,
        @Schema(description = "Access Token ???? ???? Refresh Token") String refreshToken,
        @Schema(description = "?? ??", example = "Bearer") String grantType,
        @Schema(description = "Access Token ???? ?? ??(ms)") long accessTokenExpiresIn,
        @Schema(description = "?? ????? ?? ?????? ??") boolean isNewMember,
        @Schema(description = "???? ?? ??") MemberResponse member
) {

    public static TokenResponse of(String accessToken, String refreshToken, long accessTokenExpiresIn,
                                    boolean isNewMember, MemberResponse member) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", accessTokenExpiresIn, isNewMember, member);
    }
}
