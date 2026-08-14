package domain.beauty.shortform.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "전체 스킨케어 루틴 분석 요청")
public record CreateShortformAnalysisRequest(
        @Schema(
                description = "분석할 공개 YouTube Shorts 또는 일반 영상 URL",
                example = "https://www.youtube.com/shorts/t1S24pgO2XQ"
        )
        @NotBlank(message = "영상 URL을 입력해 주세요.")
        @Size(max = 2_048, message = "영상 URL이 너무 깁니다.")
        String videoUrl
) {
}
