package domain.inventory;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByNameContainingIgnoreCase(String keyword);

    List<Product> findAllByOrderByIdAsc();

    Optional<Product> findByName(String name);

    Optional<Product> findByNormalizedName(String normalizedName);

    Optional<Product> findFirstByNameIgnoreCase(String name);

    Optional<Product> findFirstByNameIgnoreCaseAndBrandIgnoreCase(String name, String brand);
}
