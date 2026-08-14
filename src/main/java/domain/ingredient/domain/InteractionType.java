package domain.ingredient.domain;

/**
 * 성분 간 궁합 타입
 * SUBSTITUTE는 화면상 "호환" 라벨로 표시 (API 명세서 4.3 참고).
 */
public enum InteractionType {

    CONFLICT,    // 충돌
    SYNERGY,     // 시너지
    SUBSTITUTE,  // 호환
    NEUTRAL      // 중립
}
