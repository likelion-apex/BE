package domain.beauty.domain;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "영상에서 추출하고 서버가 검증한 뷰티 루틴")
public record BeautyRoutineAnalysis(
	@Schema(description = "응답 스키마 버전", example = "1.0")
	String schemaVersion,
	@Schema(description = "영상 분석 상태")
	AnalysisStatus analysisStatus,
	@Schema(description = "영상의 대표 루틴 유형")
	RoutineType routineType,
	@Schema(description = "영상 루틴 요약", example = "토너부터 수분크림까지 순서대로 적용하는 스킨케어 루틴")
	String summary,
	@Schema(description = "영상 타임스탬프순 적용 단계. 분석할 수 없는 영상에서는 빈 배열입니다.")
	List<Step> steps,
	@Schema(description = "분석 시 확인된 주의사항", example = "[\"일부 제품 라벨을 확인할 수 없습니다.\"]")
	List<String> warnings
) {

	@Schema(description = "영상 분석 완료 상태")
	public enum AnalysisStatus {
		COMPLETE,
		PARTIAL,
		NOT_BEAUTY_ROUTINE,
		VIDEO_UNAVAILABLE
	}

	@Schema(description = "뷰티 루틴 유형")
	public enum RoutineType {
		SKINCARE,
		FULL_MAKEUP,
		BASE_MAKEUP,
		EYE_MAKEUP,
		LIP_MAKEUP,
		MIXED,
		UNKNOWN
	}

	@Schema(description = "제품 사용 목적의 근거")
	public enum PurposeBasis {
		DIRECTLY_STATED,
		GENERAL_INFERENCE
	}

	@Schema(description = "제품 식별 수준")
	public enum IdentificationLevel {
		EXACT_PRODUCT,
		CATEGORY_ONLY,
		UNKNOWN
	}

	@Schema(description = "영상에서 확인한 근거 출처")
	public enum EvidenceSource {
		VISUAL_LABEL,
		ON_SCREEN_TEXT,
		SPEECH,
		CAPTION,
		VISUAL_USAGE
	}

	@Schema(description = "뷰티 제품을 적용한 하나의 영상 단계")
	public record Step(
		@Schema(description = "타임스탬프순 단계 번호", example = "1", minimum = "1")
		int order,
		@Schema(description = "시작 시각(MM:SS)", example = "00:02", pattern = "^\\d{2}:[0-5]\\d$")
		String startTime,
		@Schema(description = "종료 시각(MM:SS). 확인할 수 없으면 null", example = "00:06", pattern = "^\\d{2}:[0-5]\\d$", nullable = true)
		String endTime,
		@Schema(description = "제품 적용 부위", example = "얼굴 전체")
		String applicationArea,
		@Schema(description = "수행 동작", example = "도포")
		String action,
		@Schema(description = "적용 기법", example = "손바닥으로 가볍게 눌러 흡수")
		String technique,
		@Schema(description = "제품 사용 목적", example = "피부 보습")
		String purpose,
		@Schema(description = "사용 목적을 판단한 근거")
		PurposeBasis purposeBasis,
		@Schema(description = "사용 도구. 손으로 적용하면 null", example = "퍼프", nullable = true)
		String applicator,
		@Schema(description = "제품 식별 수준")
		IdentificationLevel identificationLevel,
		@Schema(description = "제품 카테고리", example = "수분크림")
		String category,
		@Schema(description = "직접 근거로 확인된 브랜드. 카테고리만 식별하면 null", example = "구달", nullable = true)
		String brand,
		@Schema(description = "직접 근거로 확인된 상품명. 카테고리만 식별하면 null", example = "어성초 히알루론 수딩 크림", nullable = true)
		String productName,
		@Schema(description = "제품 세부 타입·색상. 확인할 수 없으면 null", nullable = true)
		String variant,
		@Schema(description = "브랜드·상품명을 확인한 영상 속 원문. 정확 상품이 아니면 null", nullable = true)
		String identityEvidenceText,
		@Schema(description = "영상에서 관찰된 색상", example = "투명", nullable = true)
		String observedColor,
		@Schema(description = "해당 단계의 근거 출처")
		List<EvidenceSource> evidenceSources,
		@Schema(description = "영상 근거 요약", example = "화면 자막과 용기 라벨에서 제품명을 확인함")
		String evidenceSummary,
		@Schema(description = "분석 확신도", example = "0.92", minimum = "0", maximum = "1")
		double confidence
	) {
	}
}
