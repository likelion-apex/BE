package domain.member.dto.request;

import domain.member.SkinConcern;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "피부 고민 변경 요청")
public record SkinConcernsUpdateRequest(

        @Schema(description = "피부 고민 목록 (다중 선택, 목록 전체를 교체함)", example = "[\"속건조\", \"여드름\"]")
        @NotNull(message = "피부 고민 목록은 필수입니다.")
        List<SkinConcern> skinConcerns
) {
}
