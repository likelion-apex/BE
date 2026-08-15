package domain.ingredient.client;

import domain.ingredient.domain.AnalysisGrade;

/**
 * ChatGPT AI 루틴 분석(4.5) 결과를 담는 내부 전용 값 객체.
 */
public record GradeAnalysisResult(AnalysisGrade grade, String comment) {
}
