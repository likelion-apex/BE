package feat.apex_BE.beauty.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "YouTube 뷰티 루틴 분석 결과")
public record BeautyRoutineAnalysisResult(
	@Schema(description = "YouTube 영상 ID", example = "-PC1SkLxtvo")
	String videoId,
	@Schema(description = "정규화된 YouTube 영상 URL", example = "https://www.youtube.com/watch?v=-PC1SkLxtvo")
	String youtubeUrl,
	@Schema(description = "분석에 사용된 Gemini 모델", example = "gemini-3.6-flash")
	String model,
	@Schema(description = "Gemini 토큰 사용량")
	TokenUsage usage,
	@Schema(description = "정규화된 뷰티 루틴 분석 데이터")
	BeautyRoutineAnalysis analysis
) {

	@Schema(description = "Gemini 요청의 토큰 사용량")
	public record TokenUsage(
		@Schema(description = "입력 토큰 수", example = "7200")
		@JsonProperty("total_input_tokens") long totalInputTokens,
		@Schema(description = "출력 토큰 수", example = "900")
		@JsonProperty("total_output_tokens") long totalOutputTokens,
		@Schema(description = "사고 토큰 수", example = "300")
		@JsonProperty("total_thought_tokens") long totalThoughtTokens,
		@Schema(description = "전체 토큰 수", example = "8400")
		@JsonProperty("total_tokens") long totalTokens
	) {
	}
}
