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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class InventoryAiCacheService {

    static final String INGREDIENT_VERSION = "v1";
    static final String PERSONALIZED_VERSION = "v1";

    private final InventoryAiCacheRepository cacheRepository;
    private final InventoryAiProperties properties;
    private final ObjectMapper objectMapper;

    public static String ingredientKey(String productName) {
        return "ing:" + ProductNameNormalizer.canonicalKey(productName) + ":" + INGREDIENT_VERSION;
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
        return cacheRepository.findById(cacheKey)
                .filter(entry -> !entry.expired(LocalDateTime.now()))
                .map(entry -> objectMapper.readTree(entry.getPayload()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(String cacheKey, Object payload) {
        if (cacheKey == null || cacheKey.isBlank() || payload == null) {
            return;
        }
        String json = objectMapper.writeValueAsString(payload);
        LocalDateTime expiresAt = LocalDateTime.now().plus(properties.getAiCacheTtl());
        cacheRepository.findById(cacheKey)
                .ifPresentOrElse(
                        existing -> existing.refresh(json, expiresAt),
                        () -> cacheRepository.save(new InventoryAiCache(cacheKey, json, expiresAt)));
    }
}
