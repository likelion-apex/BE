package feat.apex_BE.beauty.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "YouTube 뷰티 루틴 분석 요청")
public record AnalyzeBeautyRoutineRequest(
	@Schema(
		description = "공개 YouTube Shorts 또는 일반 영상 HTTPS URL",
		example = "https://www.youtube.com/shorts/-PC1SkLxtvo",
		maxLength = 2_048
	)
	@NotBlank(message = "YouTube URL을 입력해 주세요.")
	@Size(max = 2_048, message = "YouTube URL이 너무 깁니다.")
	String youtubeUrl
) {
}
