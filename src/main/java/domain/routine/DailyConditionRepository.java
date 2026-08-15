package domain.routine;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyConditionRepository extends JpaRepository<DailyCondition, Long> {

    Optional<DailyCondition> findByMemberIdAndLogDate(Long memberId, LocalDate logDate);
}