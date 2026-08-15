package domain.ingredient.client;

/**
 * ChatGPT 루틴 개선 방향 제시(4.6) 결과를 담는 내부 전용 값 객체.
 * 각 필드는 보유 인벤토리 중 해당 관계가 발견된 제품이 없으면 null이다.
 */
public record RoutineImprovementResult(Match synergy, Match conflict, Match duplicate) {

    public record Match(Long productId, String message) {
    }
}
