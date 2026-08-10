package domain.cosmetic.client;

/**
 * 화장품 원료성분정보 API 응답을 담는 내부 전용 값 객체.
 * casNo/description/synonym은 서비스 계층 내부 판단용으로만 쓰이고 외부 응답 DTO에는 노출하지 않는다.
 */
public record IngredientInfo(
        String koreanName,
        String englishName,
        String casNo,
        String description,
        String synonym
) {
}
