package feat.apex_BE.beauty.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BeautyRoutineAnalysisResult(
	String videoId,
	String youtubeUrl,
	String model,
	TokenUsage usage,
	BeautyRoutineAnalysis analysis
) {

	public record TokenUsage(
		@JsonProperty("total_input_tokens") long totalInputTokens,
		@JsonProperty("total_output_tokens") long totalOutputTokens,
		@JsonProperty("total_thought_tokens") long totalThoughtTokens,
		@JsonProperty("total_tokens") long totalTokens
	) {
	}
}
