package feat.apex_BE.auth.dto.response;

import feat.apex_BE.domain.member.Member;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원 정보")
public record MemberResponse(
        @Schema(description = "회원 ID") Long id,
        @Schema(description = "이메일 (카카오 동의 항목에 따라 없을 수 있음)") String email,
        @Schema(description = "닉네임") String nickname,
        @Schema(description = "프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "가입 경로") String provider,
        @Schema(description = "권한") String role
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
