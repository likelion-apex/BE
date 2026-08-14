package domain.beauty.shortform.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRoutineExtractionRepository extends JpaRepository<VideoRoutineExtraction, Long> {

    Optional<VideoRoutineExtraction> findByCacheKey(String cacheKey);
}
