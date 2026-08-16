package domain.routine.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import domain.routine.domain.RoutineLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineLogRepository extends JpaRepository<RoutineLog, Long> {

    Optional<RoutineLog> findByMemberIdAndLogDateAndRoutineId(Long memberId, LocalDate logDate, Long routineId);

    /** 6.7 캘린더 월별 조회용 - 해당 월 1일~말일 범위. */
    @EntityGraph(attributePaths = "steps")
    List<RoutineLog> findByMemberIdAndLogDateBetween(Long memberId, LocalDate start, LocalDate end);

    /** 6.8 특정 날짜 조회용 - routineId 상관없이 그날의 로그 전체(DAY+NIGHT 등). */
    @EntityGraph(attributePaths = "steps")
    List<RoutineLog> findByMemberIdAndLogDate(Long memberId, LocalDate logDate);

    /** 6.11 루틴 삭제 시 연관 RoutineLog를 먼저 정리하기 위한 조회. */
    List<RoutineLog> findByRoutineId(Long routineId);
}
