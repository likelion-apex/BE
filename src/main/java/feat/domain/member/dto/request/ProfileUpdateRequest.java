package feat.domain.member.dto.request;

import feat.domain.member.Member;
import feat.domain.member.SkinConcern;
import feat.domain.member.SkinType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "프로필(닉네임/피부타입/피부고민) 통합 수정 요청")
public record ProfileUpdateRequest(

        @Schema(description = "서비스에서 사용할 닉네임", example = "레오")
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = Member.NICKNAME_MAX_LENGTH, message = "닉네임은 최대 " + Member.NICKNAME_MAX_LENGTH + "자까지 입력할 수 있습니다.")
        String nickname,

        @Schema(description = "피부 타입", example = "지성")
        @NotNull(message = "피부 타입은 필수입니다.")
        SkinType skinType,

        @Schema(description = "피부 고민 목록 (다중 선택, 목록 전체를 교체함)", example = "[\"속건조\", \"여드름\"]")
        @NotNull(message = "피부 고민 목록은 필수입니다.")
        List<SkinConcern> skinConcerns
) {
}
