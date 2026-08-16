package domain.routine.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "routine_log_steps")
public class RoutineLogStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "routine_log_id", nullable = false)
    private RoutineLog routineLog;

    @Column(name = "routine_step_id", nullable = false)
    private Long routineStepId;

    @Column(name = "step_order", nullable = false)
    private int order;

    @Column(nullable = false)
    private boolean completed;

    public RoutineLogStep(RoutineLog routineLog, Long routineStepId, int order) {
        this.routineLog = routineLog;
        this.routineStepId = routineStepId;
        this.order = order;
        this.completed = false;
    }
}
