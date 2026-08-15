package domain.routine;

import domain.beauty.shortform.domain.RoutineSaveType;
import domain.beauty.shortform.domain.ShortformAnalysis;
import domain.member.Member;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "routines", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_routine_member_analysis_save_type",
                columnNames = {"member_id", "source_analysis_id", "save_type"}
        )
})
public class Routine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "source_analysis_id", nullable = true)
    private ShortformAnalysis sourceAnalysis;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "routine_type", nullable = false, length = 20)
    private RoutineType routineType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoutineStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "save_type", nullable = false, length = 20)
    private RoutineSaveType saveType;

    @OneToMany(mappedBy = "routine", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoutineStep> steps = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Routine(
            Member member,
            ShortformAnalysis sourceAnalysis,
            String name,
            RoutineType routineType,
            RoutineStatus status,
            RoutineSaveType saveType
    ) {
        this.member = member;
        this.sourceAnalysis = sourceAnalysis;
        this.name = name;
        this.routineType = routineType;
        this.status = status;
        this.saveType = saveType;
    }

    public void addStep(RoutineStep step) {
        steps.add(step);
    }

    public List<RoutineStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }
}
