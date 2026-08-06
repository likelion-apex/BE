package feat.apex_BE.beauty.api;

import feat.apex_BE.beauty.application.BeautyRoutineAnalysisService;
import feat.apex_BE.beauty.domain.BeautyRoutineAnalysisResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/beauty-routines")
public class BeautyRoutineController {

	private final BeautyRoutineAnalysisService analysisService;

	public BeautyRoutineController(BeautyRoutineAnalysisService analysisService) {
		this.analysisService = analysisService;
	}

	@PostMapping("/analyze")
	public ResponseEntity<BeautyRoutineAnalysisResult> analyze(
		@Valid @RequestBody AnalyzeBeautyRoutineRequest request
	) {
		return ResponseEntity.ok(analysisService.analyze(request.youtubeUrl()));
	}
}
