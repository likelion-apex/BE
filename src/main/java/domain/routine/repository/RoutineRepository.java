package domain.routine.repository;

import domain.beauty.shortform.domain.RoutineSaveType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import domain.routine.domain.Routine;
import domain.routine.domain.RoutineStatus;
import domain.routine.domain.RoutineType;
import org.springframework.data.domain.Sort;
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

    /** 6.10 보관함 루틴 상세 조회용 소유권 검증 조회. */
    @EntityGraph(attributePaths = "steps")
    Optional<Routine> findByIdAndMemberId(Long id, Long memberId);

    /** 6.9 보관함 목록 - year 지정(해당 연도 1/1 00:00 ~ 12/31 23:59:59). */
    @EntityGraph(attributePaths = "steps")
    List<Routine> findByMemberIdAndStatusAndCreatedAtBetween(
            Long memberId, RoutineStatus status, LocalDateTime start, LocalDateTime end, Sort sort);

    /** 6.9 보관함 목록 - year 미지정(최근 3년 기본값). */
    @EntityGraph(attributePaths = "steps")
    List<Routine> findByMemberIdAndStatusAndCreatedAtAfter(
            Long memberId, RoutineStatus status, LocalDateTime after, Sort sort);
}
