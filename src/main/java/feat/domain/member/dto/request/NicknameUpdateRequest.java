package feat.domain.member.dto.request;

import feat.domain.member.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "닉네임 변경 요청")
public record NicknameUpdateRequest(

        @Schema(description = "서비스에서 사용할 닉네임", example = "레오")
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = Member.NICKNAME_MAX_LENGTH, message = "닉네임은 최대 " + Member.NICKNAME_MAX_LENGTH + "자까지 입력할 수 있습니다.")
        String nickname
) {
}
