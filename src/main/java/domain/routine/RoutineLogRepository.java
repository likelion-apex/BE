package domain.routine;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineLogRepository extends JpaRepository<RoutineLog, Long> {

    Optional<RoutineLog> findByMemberIdAndLogDateAndRoutineId(Long memberId, LocalDate logDate, Long routineId);
}
