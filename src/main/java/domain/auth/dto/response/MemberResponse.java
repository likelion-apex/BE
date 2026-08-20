package domain.auth.dto.response;

import domain.member.Member;
import domain.member.SkinConcern;
import domain.member.SkinType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "회원 정보")
public record MemberResponse(
        @Schema(description = "회원 ID") Long id,
        @Schema(description = "계정 이메일 (없을 수 있음)") String email,
        @Schema(description = "닉네임") String nickname,
        @Schema(description = "프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "가입 경로") String provider,
        @Schema(description = "권한") String role,
        @Schema(description = "피부 타입 (온보딩 전에는 null)", example = "지성") SkinType skinType,
        @Schema(description = "피부 고민 목록 (온보딩 전에는 빈 배열)") List<SkinConcern> skinConcerns
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getProfileImageUrl(),
                member.getProvider().name(),
                member.getRole().name(),
                member.getSkinType(),
                member.getSkinConcerns().stream().sorted().toList()
        );
    }
}
