package domain.ingredient.domain;

import lombok.Getter;

/**
 * AI 루틴 분석(4.5)의 종합 등급. 명세서 예시({@code "grade": "GOOD"})를 따라 enum name 그대로
 * JSON에 직렬화되며, label은 AI 프롬프트 구성 등 내부 용도로만 사용한다.
 */
@Getter
public enum AnalysisGrade {

    SAFE("안전"),
    MEH("아쉬움"),
    GOOD("좋음"),
    RISK("위험");

    private final String label;

    AnalysisGrade(String label) {
        this.label = label;
    }
}
