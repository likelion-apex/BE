package domain.ingredient.domain;

import lombok.Getter;

@Getter
public enum RiskLevel {

    LOW("낮음"),
    MID("보통"),
    HIGH("위험");

    private final String description;

    RiskLevel(String description) {
        this.description = description;
    }

    public static RiskLevel fromEwgGrade(Integer grade) {
        if (grade == null) return LOW;
        if (grade <= 2) return LOW;
        if (grade <= 6) return MID;
        return HIGH;
    }
}
