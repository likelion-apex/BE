package domain.routine.repository;

import java.time.LocalDate;
import java.util.Optional;

import domain.routine.domain.DailyCondition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyConditionRepository extends JpaRepository<DailyCondition, Long> {

    Optional<DailyCondition> findByMemberIdAndLogDate(Long memberId, LocalDate logDate);

    void deleteByMemberIdAndLogDate(Long memberId, LocalDate logDate);
}