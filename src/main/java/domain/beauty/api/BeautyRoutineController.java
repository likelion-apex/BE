package domain.beauty.api;

import domain.beauty.application.BeautyRoutineAnalysisService;
import domain.beauty.domain.BeautyRoutineAnalysisResult;
import global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/beauty-routines")
@Tag(name = "Beauty Routine", description = "YouTube 영상의 스킨케어·메이크업 루틴 분석 API")
public class BeautyRoutineController {

	private final BeautyRoutineAnalysisService analysisService;

	public BeautyRoutineController(BeautyRoutineAnalysisService analysisService) {
		this.analysisService = analysisService;
	}

	@PostMapping("/analyze")
	@Operation(
		summary = "YouTube 뷰티 루틴 분석",
		description = """
			공개 YouTube Shorts 또는 일반 영상 URL을 Gemini로 분석합니다.
			응답에는 정규화된 영상 URL, 모델과 토큰 사용량, 루틴 유형 및 타임스탬프순 단계가 포함됩니다.
			동일한 영상·모델·프롬프트 조합의 성공 결과는 30일간 캐시됩니다.
			""",
		requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
			required = true,
			description = "분석할 공개 YouTube 영상 URL",
			content = @Content(
				schema = @Schema(implementation = AnalyzeBeautyRoutineRequest.class),
				examples = @ExampleObject(value = """
					{"youtubeUrl":"https://www.youtube.com/shorts/-PC1SkLxtvo"}
					""")
			)
		)
	)
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200",
			description = "뷰티 루틴 분석 성공",
			content = @Content(schema = @Schema(implementation = BeautyRoutineAnalysisResult.class))
		),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "지원하지 않거나 잘못된 YouTube URL",
			content = @Content(schema = @Schema(implementation = ApiResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token 누락 또는 오류",
			content = @Content(schema = @Schema(implementation = ApiResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Gemini 응답 형식 또는 분석 결과 오류",
			content = @Content(schema = @Schema(implementation = ApiResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Gemini 설정 누락, 할당량 초과 또는 일시적 장애",
			content = @Content(schema = @Schema(implementation = ApiResponse.class)))
	})
	public ResponseEntity<BeautyRoutineAnalysisResult> analyze(
		@Valid @RequestBody AnalyzeBeautyRoutineRequest request
	) {
		return ResponseEntity.ok(analysisService.analyze(request.youtubeUrl()));
	}
}
