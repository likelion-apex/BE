package domain.member.dto.request;

import domain.member.SkinType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "피부 타입 변경 요청")
public record SkinTypeUpdateRequest(

        @Schema(description = "피부 타입", example = "지성")
        @NotNull(message = "피부 타입은 필수입니다.")
        SkinType skinType
) {
}
