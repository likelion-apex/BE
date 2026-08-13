package domain.inventory.client;

import java.util.List;

/**
 * ChatGPT 맞춤 분석 결과를 담는 내부 전용 값 객체.
 */
public record PersonalizedAnalysisResult(int score, List<Keyword> keywords) {

    public record Keyword(String keyword, String reason) {
    }
}
