package domain.inventory;

/**
 * 화장품 카테고리. 프론트 CATEGORY_NAME_MAP과 동일한 10종이며,
 * 상품이 인벤토리에 처음 등록될 때 AI가 자동으로 분류하여 Product에 저장한다.
 * 선언 순서는 스킨케어 루틴 단계 순서와 일치하며, RoutineService가 이 순서로 루틴 스텝을 생성한다.
 */
public enum ProductCategory {
    SKIN_TONER,      // 스킨/토너
    LOTION,          // 로션/에멀전
    ESSENCE_SERUM,   // 에센스/앰플/세럼
    FACEOIL,         // 페이스오일
    CREAM,           // 크림
    EYECARE,         // 아이케어
    MIST,            // 미스트,젤
    SKIN_TONERPAD,   // 스킨/토너 패드
    BAM,             // 밤/멀티밤
    ETC              // 기타
}
