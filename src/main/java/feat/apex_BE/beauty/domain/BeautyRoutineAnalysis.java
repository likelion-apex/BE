package feat.apex_BE.beauty.domain;

import java.util.List;

public record BeautyRoutineAnalysis(
	String schemaVersion,
	AnalysisStatus analysisStatus,
	RoutineType routineType,
	String summary,
	List<Step> steps,
	List<String> warnings
) {

	public enum AnalysisStatus {
		COMPLETE,
		PARTIAL,
		NOT_BEAUTY_ROUTINE,
		VIDEO_UNAVAILABLE
	}

	public enum RoutineType {
		SKINCARE,
		FULL_MAKEUP,
		BASE_MAKEUP,
		EYE_MAKEUP,
		LIP_MAKEUP,
		MIXED,
		UNKNOWN
	}

	public enum PurposeBasis {
		DIRECTLY_STATED,
		GENERAL_INFERENCE
	}

	public enum IdentificationLevel {
		EXACT_PRODUCT,
		CATEGORY_ONLY,
		UNKNOWN
	}

	public enum EvidenceSource {
		VISUAL_LABEL,
		ON_SCREEN_TEXT,
		SPEECH,
		CAPTION,
		VISUAL_USAGE
	}

	public record Step(
		int order,
		String startTime,
		String endTime,
		String applicationArea,
		String action,
		String technique,
		String purpose,
		PurposeBasis purposeBasis,
		String applicator,
		IdentificationLevel identificationLevel,
		String category,
		String brand,
		String productName,
		String variant,
		String identityEvidenceText,
		String observedColor,
		List<EvidenceSource> evidenceSources,
		String evidenceSummary,
		double confidence
	) {
	}
}
