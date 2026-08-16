package domain.inventory;

/**
 * 화장품 카테고리. 프론트 CATEGORY_NAME_MAP과 동일한 9종이며,
 * 상품이 인벤토리에 처음 등록될 때 AI가 자동으로 분류하여 Product에 저장한다.
 */
public enum ProductCategory {
    SKIN_TONER,  // 스킨/토너
    SERUM,       // 세럼/앰플
    CREAM,       // 크림
    ESSENCE,     // 에센스
    LOTION,      // 로션/에멀전
    SUNCREAM,    // 선케어
    CLEANSER,    // 클렌징
    MASK,        // 마스크/팩
    ETC          // 기타
}
