package domain.inventory.ai;

import java.util.List;

/**
 * AI가 반환한 성분 하나의 배합목적/효능/위험도 원본 값. 모두 정규화 전 원본 문자열이며,
 * 실제 응답 직렬화 시에는 각각 {@code domain.inventory.dto.response.IngredientPurposeCategory.fromRaw(...)},
 * {@code SkinEfficacyTag.fromRaw(...)}, {@code IngredientRiskLevel.fromRaw(...)}로 변환한다.
 */
public record IngredientAiDetail(List<String> purposes, List<String> efficacyTags, String riskLevel) {
}
