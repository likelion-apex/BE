package domain.routine.domain;

import domain.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 하루 1회 기록하는 피부 컨디션 체크. Routine과 완전히 독립적이라
 * 활성 루틴이 없어도 컨디션만 단독으로 기록할 수 있다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "daily_conditions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_daily_condition_member_date", columnNames = {"member_id", "log_date"})
})
public class DailyCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 20)
    private DailyConditionType condition;

    @Column(length = 500)
    private String memo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public DailyCondition(Member member, LocalDate logDate, DailyConditionType condition, String memo) {
        this.member = member;
        this.logDate = logDate;
        this.condition = condition;
        this.memo = memo;
    }

    public void update(DailyConditionType condition, String memo) {
        this.condition = condition;
        this.memo = memo;
    }
}
