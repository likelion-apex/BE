package domain.beauty.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import domain.beauty.domain.BeautyRoutineAnalysis;
import domain.beauty.domain.BeautyRoutineAnalysis.AnalysisStatus;
import domain.beauty.domain.BeautyRoutineAnalysis.EvidenceSource;
import domain.beauty.domain.BeautyRoutineAnalysis.IdentificationLevel;
import domain.beauty.domain.BeautyRoutineAnalysis.PurposeBasis;
import domain.beauty.domain.BeautyRoutineAnalysis.RoutineType;
import domain.beauty.domain.BeautyRoutineAnalysis.Step;
import domain.beauty.exception.BeautyRoutineException.InvalidGeminiResponse;
import org.junit.jupiter.api.Test;

class BeautyRoutineAnalysisValidatorTest {

	private final BeautyRoutineAnalysisValidator validator = new BeautyRoutineAnalysisValidator();

	@Test
	void keepsExactProductOnlyWhenDirectEvidenceAndConfidenceAreSufficient() {
		BeautyRoutineAnalysis validated = validator.validateAndNormalize(analysis(List.of(
			step(2, "00:07", IdentificationLevel.EXACT_PRODUCT, 0.95, List.of(EvidenceSource.ON_SCREEN_TEXT)),
			step(1, "00:02", IdentificationLevel.EXACT_PRODUCT, 0.84, List.of(EvidenceSource.VISUAL_LABEL)),
			step(3, "00:09", IdentificationLevel.EXACT_PRODUCT, 0.99, List.of(EvidenceSource.VISUAL_USAGE))
		)));

		assertThat(validated.steps()).extracting(Step::order).containsExactly(1, 2, 3);
		assertThat(validated.steps()).extracting(Step::startTime).containsExactly("00:02", "00:07", "00:09");
		assertThat(validated.steps().get(0).identificationLevel()).isEqualTo(IdentificationLevel.CATEGORY_ONLY);
		assertThat(validated.steps().get(0).brand()).isNull();
		assertThat(validated.steps().get(1).identificationLevel()).isEqualTo(IdentificationLevel.EXACT_PRODUCT);
		assertThat(validated.steps().get(1).brand()).isEqualTo("브랜드");
		assertThat(validated.steps().get(2).identificationLevel()).isEqualTo(IdentificationLevel.CATEGORY_ONLY);
	}

	@Test
	void clearsStepsWhenVideoIsUnavailable() {
		BeautyRoutineAnalysis unavailable = new BeautyRoutineAnalysis(
			"1.0",
			AnalysisStatus.VIDEO_UNAVAILABLE,
			RoutineType.UNKNOWN,
			"영상을 분석할 수 없습니다.",
			List.of(step(1, "00:01", IdentificationLevel.CATEGORY_ONLY, 0.9, List.of(EvidenceSource.VISUAL_USAGE))),
			List.of("비공개 영상")
		);

		assertThat(validator.validateAndNormalize(unavailable).steps()).isEmpty();
	}

	@Test
	void rejectsInvalidTimestamp() {
		assertThatThrownBy(() -> validator.validateAndNormalize(analysis(List.of(
			step(1, "1:99", IdentificationLevel.CATEGORY_ONLY, 0.9, List.of(EvidenceSource.VISUAL_USAGE))
		))))
			.isInstanceOf(InvalidGeminiResponse.class);
	}

	private BeautyRoutineAnalysis analysis(List<Step> steps) {
		return new BeautyRoutineAnalysis(
			"1.0",
			AnalysisStatus.COMPLETE,
			RoutineType.SKINCARE,
			"스킨케어 루틴입니다.",
			steps,
			List.of()
		);
	}

	private Step step(
		int order,
		String startTime,
		IdentificationLevel level,
		double confidence,
		List<EvidenceSource> evidenceSources
	) {
		return new Step(
			order,
			startTime,
			null,
			"얼굴",
			"제품을 바릅니다.",
			"손으로 펴 바릅니다.",
			"보습",
			PurposeBasis.GENERAL_INFERENCE,
			"손",
			level,
			"세럼",
			"브랜드",
			"제품명",
			null,
			"브랜드 제품명",
			"투명",
			evidenceSources,
			"영상에서 사용 장면을 확인했습니다.",
			confidence
		);
	}
}
