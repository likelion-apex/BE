package feat.auth.dto.response;

import feat.domain.member.Member;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "?? ??")
public record MemberResponse(
        @Schema(description = "?? ID") Long id,
        @Schema(description = "??? (??? ?? ??? ?? ?? ? ??)") String email,
        @Schema(description = "???") String nickname,
        @Schema(description = "??? ??? URL") String profileImageUrl,
        @Schema(description = "?? ??") String provider,
        @Schema(description = "??") String role
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getProfileImageUrl(),
                member.getProvider().name(),
                member.getRole().name()
        );
    }
}
