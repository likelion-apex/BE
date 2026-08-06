package feat.apex_BE.beauty.support;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import feat.apex_BE.beauty.domain.BeautyRoutineAnalysis;
import feat.apex_BE.beauty.domain.BeautyRoutineAnalysis.EvidenceSource;
import feat.apex_BE.beauty.domain.BeautyRoutineAnalysis.IdentificationLevel;
import feat.apex_BE.beauty.domain.BeautyRoutineAnalysis.Step;
import feat.apex_BE.beauty.exception.BeautyRoutineException.InvalidGeminiResponse;
import org.springframework.stereotype.Component;

@Component
public class BeautyRoutineAnalysisValidator {

	private static final Pattern TIMESTAMP = Pattern.compile("^(\\d{2}):([0-5]\\d)$");
	private static final EnumSet<EvidenceSource> DIRECT_IDENTITY_EVIDENCE = EnumSet.of(
		EvidenceSource.VISUAL_LABEL,
		EvidenceSource.ON_SCREEN_TEXT,
		EvidenceSource.SPEECH,
		EvidenceSource.CAPTION
	);

	public BeautyRoutineAnalysis validateAndNormalize(BeautyRoutineAnalysis analysis) {
		if (analysis == null || !"1.0".equals(analysis.schemaVersion())) {
			throw new InvalidGeminiResponse("Gemini 응답의 스키마 버전이 올바르지 않습니다.");
		}
		if (analysis.analysisStatus() == null || analysis.routineType() == null || isBlank(analysis.summary())) {
			throw new InvalidGeminiResponse("Gemini 응답의 필수 분석 정보가 누락되었습니다.");
		}

		List<String> warnings = analysis.warnings() == null ? List.of() : List.copyOf(analysis.warnings());
		if (analysis.analysisStatus() == BeautyRoutineAnalysis.AnalysisStatus.NOT_BEAUTY_ROUTINE
			|| analysis.analysisStatus() == BeautyRoutineAnalysis.AnalysisStatus.VIDEO_UNAVAILABLE) {
			return new BeautyRoutineAnalysis(
				analysis.schemaVersion(),
				analysis.analysisStatus(),
				analysis.routineType(),
				analysis.summary(),
				List.of(),
				warnings
			);
		}

		List<Step> rawSteps = analysis.steps() == null ? List.of() : analysis.steps();
		List<Step> normalizedSteps = rawSteps.stream()
			.map(this::validateAndSanitizeStep)
			.sorted(Comparator.comparingInt(step -> timestampSeconds(step.startTime())))
			.toList();

		List<Step> orderedSteps = java.util.stream.IntStream.range(0, normalizedSteps.size())
			.mapToObj(index -> withOrder(normalizedSteps.get(index), index + 1))
			.toList();

		return new BeautyRoutineAnalysis(
			analysis.schemaVersion(),
			analysis.analysisStatus(),
			analysis.routineType(),
			analysis.summary(),
			orderedSteps,
			warnings
		);
	}

	private Step validateAndSanitizeStep(Step step) {
		if (step == null
			|| isBlank(step.applicationArea())
			|| isBlank(step.action())
			|| isBlank(step.technique())
			|| isBlank(step.purpose())
			|| step.purposeBasis() == null
			|| step.identificationLevel() == null
			|| isBlank(step.category())
			|| isBlank(step.evidenceSummary())
			|| step.evidenceSources() == null
			|| step.evidenceSources().isEmpty()
			|| step.confidence() < 0
			|| step.confidence() > 1) {
			throw new InvalidGeminiResponse("Gemini 루틴 단계의 필수 정보가 누락되거나 범위를 벗어났습니다.");
		}

		int start = timestampSeconds(step.startTime());
		if (step.endTime() != null && timestampSeconds(step.endTime()) < start) {
			throw new InvalidGeminiResponse("루틴 단계의 종료 시각이 시작 시각보다 빠릅니다.");
		}

		IdentificationLevel level = step.identificationLevel();
		String brand = step.brand();
		String productName = step.productName();
		String variant = step.variant();
		String identityEvidenceText = step.identityEvidenceText();
		boolean hasDirectEvidence = step.evidenceSources().stream().anyMatch(DIRECT_IDENTITY_EVIDENCE::contains);
		boolean exactProductAccepted = level == IdentificationLevel.EXACT_PRODUCT
			&& !isBlank(brand)
			&& !isBlank(productName)
			&& !isBlank(identityEvidenceText)
			&& step.confidence() >= 0.85
			&& hasDirectEvidence;

		if (!exactProductAccepted) {
			level = level == IdentificationLevel.UNKNOWN
				? IdentificationLevel.UNKNOWN
				: IdentificationLevel.CATEGORY_ONLY;
			brand = null;
			productName = null;
			variant = null;
			identityEvidenceText = null;
		}

		return new Step(
			step.order(),
			step.startTime(),
			step.endTime(),
			step.applicationArea(),
			step.action(),
			step.technique(),
			step.purpose(),
			step.purposeBasis(),
			step.applicator(),
			level,
			step.category(),
			brand,
			productName,
			variant,
			identityEvidenceText,
			step.observedColor(),
			List.copyOf(step.evidenceSources()),
			step.evidenceSummary(),
			step.confidence()
		);
	}

	private Step withOrder(Step step, int order) {
		return new Step(
			order,
			step.startTime(),
			step.endTime(),
			step.applicationArea(),
			step.action(),
			step.technique(),
			step.purpose(),
			step.purposeBasis(),
			step.applicator(),
			step.identificationLevel(),
			step.category(),
			step.brand(),
			step.productName(),
			step.variant(),
			step.identityEvidenceText(),
			step.observedColor(),
			step.evidenceSources(),
			step.evidenceSummary(),
			step.confidence()
		);
	}

	private int timestampSeconds(String timestamp) {
		if (timestamp == null) {
			throw new InvalidGeminiResponse("루틴 단계의 타임스탬프가 누락되었습니다.");
		}
		Matcher matcher = TIMESTAMP.matcher(timestamp);
		if (!matcher.matches()) {
			throw new InvalidGeminiResponse("타임스탬프는 MM:SS 형식이어야 합니다.");
		}
		return Integer.parseInt(matcher.group(1)) * 60 + Integer.parseInt(matcher.group(2));
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
