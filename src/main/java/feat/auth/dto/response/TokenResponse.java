package feat.apex_BE.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인/재발급 성공 시 반환되는 토큰 정보")
public record TokenResponse(
        @Schema(description = "API 인증에 사용하는 Access Token") String accessToken,
        @Schema(description = "Access Token 재발급에 사용하는 Refresh Token") String refreshToken,
        @Schema(description = "토큰 타입", example = "Bearer") String grantType,
        @Schema(description = "Access Token 만료까지 남은 시간(ms)") long accessTokenExpiresIn,
        @Schema(description = "이번 로그인으로 신규 가입되었는지 여부") boolean isNewMember,
        @Schema(description = "로그인한 회원 정보") MemberResponse member
) {

    public static TokenResponse of(String accessToken, String refreshToken, long accessTokenExpiresIn,
                                    boolean isNewMember, MemberResponse member) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", accessTokenExpiresIn, isNewMember, member);
    }
}
