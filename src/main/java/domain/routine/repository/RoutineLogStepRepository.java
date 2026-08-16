package domain.routine.repository;

import domain.routine.domain.RoutineLogStep;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineLogStepRepository extends JpaRepository<RoutineLogStep, Long> {

    /**
     * RoutineLogStep -> RoutineLog -> Member 경로로 소유권까지 함께 검증한다.
     * stepId가 없거나 다른 회원 소유면 빈 Optional을 반환한다.
     */
    Optional<RoutineLogStep> findByIdAndRoutineLog_Member_Id(Long id, Long memberId);
}