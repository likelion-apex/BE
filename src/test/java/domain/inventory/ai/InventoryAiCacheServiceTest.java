package domain.inventory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class InventoryAiCacheServiceTest {

    @Mock
    private InventoryAiCacheRepository cacheRepository;
    @Mock
    private InventoryAiProperties properties;

    private InventoryAiCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new InventoryAiCacheService(cacheRepository, properties, new ObjectMapper());
    }

    @Test
    void findReturnsEmptyWhenRepositoryFails() {
        when(cacheRepository.findById("ing:바닥토너:v1"))
                .thenThrow(new DataAccessResourceFailureException("inventory_ai_caches"));

        assertThat(cacheService.find("ing:바닥토너:v1")).isEmpty();
    }

    @Test
    void findReturnsEmptyWhenPayloadIsInvalidJson() {
        InventoryAiCache entry = new InventoryAiCache(
                "ing:바닥토너:v1", "not-json", LocalDateTime.now().plusDays(1));
        when(cacheRepository.findById("ing:바닥토너:v1")).thenReturn(Optional.of(entry));

        assertThat(cacheService.find("ing:바닥토너:v1")).isEmpty();
    }

    @Test
    void saveDoesNotThrowWhenRepositoryFails() {
        when(properties.getAiCacheTtl()).thenReturn(Duration.ofDays(30));
        when(cacheRepository.findById(any())).thenThrow(new DataAccessResourceFailureException("read-only"));

        assertThatCode(() -> cacheService.save("ing:바닥토너:v1", Map.of("ingredients", "정제수")))
                .doesNotThrowAnyException();
    }
}
