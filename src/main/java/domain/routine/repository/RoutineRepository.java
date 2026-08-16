package domain.routine.repository;

import domain.beauty.shortform.domain.RoutineSaveType;
import java.util.Optional;

import domain.routine.domain.Routine;
import domain.routine.domain.RoutineStatus;
import domain.routine.domain.RoutineType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineRepository extends JpaRepository<Routine, Long> {

    Optional<Routine> findByMemberIdAndSourceAnalysisIdAndSaveTypeAndRoutineType(
            Long memberId,
            Long sourceAnalysisId,
            RoutineSaveType saveType,
            RoutineType routineType
    );

    boolean existsByMemberIdAndStatusAndRoutineType(Long memberId, RoutineStatus status, RoutineType routineType);

    @EntityGraph(attributePaths = "steps")
    Optional<Routine> findByMemberIdAndStatusAndRoutineType(Long memberId, RoutineStatus status, RoutineType routineType);
}
