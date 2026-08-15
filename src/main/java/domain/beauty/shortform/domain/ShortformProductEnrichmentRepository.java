package domain.beauty.shortform.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortformProductEnrichmentRepository extends JpaRepository<ShortformProductEnrichment, Long> {
    Optional<ShortformProductEnrichment> findByCacheKey(String cacheKey);

    List<ShortformProductEnrichment> findByCacheKeyIn(Collection<String> cacheKeys);
}
