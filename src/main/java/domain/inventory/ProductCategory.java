package domain.inventory;

/**
 * 화장품 카테고리. 프론트 CATEGORY_NAME_MAP과 동일한 9개의 실제 스킨케어 단계 카테고리에
 * ETC(기타)를 더한 10종이며, 상품이 인벤토리에 처음 등록될 때 AI가 자동으로 분류하여
 * Product에 저장한다.
 * 선언 순서는 스킨케어 루틴 단계 순서와 일치하며, RoutineService가 이 순서로 루틴 스텝을 생성한다.
 * ETC는 특정 스킨케어 단계가 없는 잡화(선크림/클렌저/마스크 등)를 위한 카테고리로,
 * 루틴 스텝 생성 대상에서는 제외된다(RoutineService 참고).
 * AI 호출 자체가 실패한 경우(장애 등)에는 category를 null로 둔다.
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
    ETC              // 기타(선크림/클렌저/마스크 등 9종에 해당하지 않는 상품)
}
