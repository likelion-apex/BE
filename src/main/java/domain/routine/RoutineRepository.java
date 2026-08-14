package domain.routine;

import domain.beauty.shortform.domain.RoutineSaveType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineRepository extends JpaRepository<Routine, Long> {

    Optional<Routine> findByMemberIdAndSourceAnalysisIdAndSaveType(
            Long memberId,
            Long sourceAnalysisId,
            RoutineSaveType saveType
    );

    boolean existsByMemberIdAndStatus(Long memberId, RoutineStatus status);
}
