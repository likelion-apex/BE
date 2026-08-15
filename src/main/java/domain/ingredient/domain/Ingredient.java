package domain.ingredient.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ingredients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ingredient_id")
    private Long id;

    /** 영문명 없이 한글명만 관리 */
    @Column(name = "name", nullable = false)
    private String name;

    /** 레티놀 / AHA-BHA / 비타민C / 나이아신아마이드 등 */
    @Column(name = "function_tag")
    private String functionTag;

    @Column(name = "function_group")
    private String functionGroup;

    /**
     * EWG 등급 (1~10). 1~2 안전, 3~6 주의, 7 이상 위험.
     * 원본 등급을 그대로 저장하고, 화면 표시용 버킷(LOW/MID/HIGH)은 RiskLevel.fromEwgGrade()로 그때그때 변환
     */
    @Column(name = "ewg_grade")
    private Integer ewgGrade;

    @Builder
    private Ingredient(String name, String functionTag, String functionGroup, Integer ewgGrade) {
        this.name = name;
        this.functionTag = functionTag;
        this.functionGroup = functionGroup;
        this.ewgGrade = ewgGrade;
    }
}
