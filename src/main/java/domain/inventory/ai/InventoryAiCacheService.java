package domain.inventory.ai;

import domain.inventory.ProductNameNormalizer;
import domain.member.SkinConcern;
import domain.member.SkinType;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAiCacheService {

    static final String INGREDIENT_VERSION = "v3";
    static final String PERSONALIZED_VERSION = "v4";
    static final String BRAND_VERSION = "v1";
    private static final int MAX_CACHE_KEY_LENGTH = 255;

    private final InventoryAiCacheRepository cacheRepository;
    private final InventoryAiProperties properties;
    private final ObjectMapper objectMapper;

    public static String ingredientKey(String productName) {
        return "ing:" + ProductNameNormalizer.canonicalKey(productName) + ":" + INGREDIENT_VERSION;
    }

    public static String brandKey(String productName) {
        return "brand:" + ProductNameNormalizer.canonicalKey(productName) + ":" + BRAND_VERSION;
    }

    public static String personalizedKey(String productName, SkinType skinType, Set<SkinConcern> skinConcerns) {
        String type = skinType == null ? "none" : skinType.name();
        String concerns = (skinConcerns == null || skinConcerns.isEmpty())
                ? "none"
                : skinConcerns.stream()
                        .sorted(Comparator.comparing(Enum::name))
                        .map(Enum::name)
                        .collect(Collectors.joining(","));
        return "ai:" + ProductNameNormalizer.canonicalKey(productName) + ":" + type + ":" + concerns + ":"
                + PERSONALIZED_VERSION;
    }

    @Transactional(readOnly = true)
    public Optional<JsonNode> find(String cacheKey) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return Optional.empty();
        }
        try {
            return cacheRepository.findById(cacheKey)
                    .filter(entry -> !entry.expired(LocalDateTime.now()))
                    .map(entry -> objectMapper.readTree(entry.getPayload()));
        } catch (RuntimeException e) {
            log.warn("인벤토리 AI 캐시 조회 실패: cacheKey={}, message={}", cacheKey, e.getMessage());
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(String cacheKey, Object payload) {
        if (cacheKey == null || cacheKey.isBlank() || payload == null) {
            return;
        }
        if (cacheKey.length() > MAX_CACHE_KEY_LENGTH) {
            log.warn("인벤토리 AI 캐시 키가 너무 길어 저장하지 않습니다: length={}", cacheKey.length());
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            LocalDateTime expiresAt = LocalDateTime.now().plus(properties.getAiCacheTtl());
            cacheRepository.findById(cacheKey)
                    .ifPresentOrElse(
                            existing -> existing.refresh(json, expiresAt),
                            () -> cacheRepository.save(new InventoryAiCache(cacheKey, json, expiresAt)));
        } catch (RuntimeException e) {
            log.warn("인벤토리 AI 캐시 저장 실패: cacheKey={}, message={}", cacheKey, e.getMessage());
        }
    }
}
