package feat.apex_BE.beauty.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnalyzeBeautyRoutineRequest(
	@NotBlank(message = "YouTube URL을 입력해 주세요.")
	@Size(max = 2_048, message = "YouTube URL이 너무 깁니다.")
	String youtubeUrl
) {
}
