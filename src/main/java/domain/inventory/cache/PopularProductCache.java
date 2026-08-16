package domain.inventory.cache;

import domain.inventory.InventoryRepository;
import domain.inventory.Product;
import domain.inventory.ProductRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 인벤토리에 많이 담긴 상품을 인메모리로 캐싱한다.
 * 기동 시 1회 적재하고 30분마다 갱신한다.
 */
@Slf4j
@Component
public class PopularProductCache {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final int size;
    private final Map<Long, Product> cache = new ConcurrentHashMap<>();

    public PopularProductCache(
            InventoryRepository inventoryRepository,
            ProductRepository productRepository,
            @Value("${product.popular-cache.size:100}") int size) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.size = size;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        refresh();
    }

    @Scheduled(cron = "0 */30 * * * *")
    public void refresh() {
        List<Long> productIds = inventoryRepository.findTopProductIdsByInventoryCount(PageRequest.of(0, size));
        Map<Long, Product> loaded = new ConcurrentHashMap<>();
        if (!productIds.isEmpty()) {
            productRepository.findAllById(productIds)
                    .forEach(product -> loaded.put(product.getId(), product));
        }
        cache.clear();
        cache.putAll(loaded);
        log.info("인기 상품 캐시 갱신 완료: {}건", cache.size());
    }

    public Optional<Product> find(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(productId));
    }
}
