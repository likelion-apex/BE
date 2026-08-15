package domain.inventory;

/**
 * 화장품 카테고리. 상품이 인벤토리에 처음 등록될 때 AI가 자동으로 분류하여 Product에 저장한다.
 */
public enum ProductCategory {
    SKIN_TONER,              // 스킨/토너
    SKIN_TONER_PAD,          // 스킨/토너 패드
    LOTION_EMULSION,         // 로션/에멀전
    ESSENCE_AMPOULE_SERUM,   // 에센스/앰플/세럼
    FACE_OIL,                // 페이스오일
    CREAM,                   // 크림
    EYE_CARE,                // 아이케어
    MIST_GEL,                // 미스트·젤
    BALM_MULTIBALM           // 밤/멀티밤
}
