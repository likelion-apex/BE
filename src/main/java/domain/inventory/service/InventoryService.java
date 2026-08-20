package domain.inventory.service;

import domain.beauty.shortform.application.ProductCapacityNormalizer;
import domain.inventory.Inventory;
import domain.inventory.InventoryRepository;
import domain.inventory.Product;
import domain.inventory.ai.CautionIngredientCatalog;
import domain.inventory.ai.IngredientAiClient;
import domain.inventory.ai.IngredientAiDetail;
import domain.inventory.ai.InventoryAiCacheService;
import domain.inventory.ai.InventoryAiJsonSupport;
import domain.inventory.ai.PersonalizedAnalysisAiClient;
import domain.inventory.client.PersonalizedAnalysisResult;
import domain.inventory.dto.request.InventoryCreateRequest;
import domain.inventory.dto.response.AiAnalysisResponse;
import domain.inventory.dto.response.FavoriteInventoryResponse;
import domain.inventory.dto.response.FavoriteUpdateResponse;
import domain.inventory.dto.response.IngredientAnalysisResponse;
import domain.inventory.dto.response.IngredientPurposeCategory;
import domain.inventory.dto.response.IngredientRiskLevel;
import domain.inventory.dto.response.InventoryCreateResponse;
import domain.inventory.dto.response.InventoryDeleteResponse;
import domain.inventory.dto.response.InventoryListResponse;
import domain.inventory.dto.response.SkinEfficacyTag;
import domain.member.Member;
import domain.member.MemberRepository;
import global.exception.CustomException;
import global.exception.ErrorCode;
import global.util.PublicUrlResolver;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InventoryService {

    private static final int DEFAULT_FAVORITE_LIMIT = 4;
    private static final IngredientPurposeCategory DEFAULT_PURPOSE_CATEGORY =
            IngredientPurposeCategory.SKIN_CONDITIONING_AGENT;
    private static final int MAX_EFFICACY_TAGS = 2;
    private static final int RISK_SCORE_WEIGHT_LOW = 100;
    private static final int RISK_SCORE_WEIGHT_MEDIUM = 60;
    private static final int RISK_SCORE_WEIGHT_HIGH = 20;
    private static final int DEFAULT_SCORE_WITHOUT_INGREDIENTS = 70;

    private final InventoryRepository inventoryRepository;
    private final MemberRepository memberRepository;
    private final ProductService productService;
    private final IngredientAiClient ingredientAiClient;
    private final PersonalizedAnalysisAiClient personalizedAnalysisAiClient;
    private final InventoryAiCacheService inventoryAiCacheService;
    private final PublicUrlResolver publicUrlResolver;
    private final ProductCapacityNormalizer productCapacityNormalizer;
    private final CautionIngredientCatalog cautionIngredientCatalog;

    @Transactional(readOnly = true)
    public FavoriteInventoryResponse getFavorites(Long memberId, Integer limit) {
        int size = (limit == null || limit <= 0) ? DEFAULT_FAVORITE_LIMIT : limit;
        long totalFavoriteCount = inventoryRepository.countByMemberIdAndFavoriteTrue(memberId);
        List<Inventory> favorites = inventoryRepository
                .findAllByMemberIdAndFavoriteTrueOrderByCreatedAtDesc(memberId, PageRequest.of(0, size));
        return FavoriteInventoryResponse.of(totalFavoriteCount, favorites, publicUrlResolver);
    }

    @Transactional(readOnly = true)
    public InventoryListResponse getAll(Long memberId) {
        return InventoryListResponse.from(
                inventoryRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId), publicUrlResolver);
    }

    public InventoryCreateResponse create(Long memberId, InventoryCreateRequest request) {
        Member member = findMember(memberId);
        Product product = productService.findOrCreate(request.productName());
        if (inventoryRepository.existsByMemberIdAndProductId(memberId, product.getId())) {
            throw new CustomException(ErrorCode.INVENTORY_ALREADY_EXISTS);
        }
        Inventory inventory = Inventory.builder()
                .member(member)
                .product(product)
                .build();
        return InventoryCreateResponse.from(inventoryRepository.save(inventory));
    }

    public InventoryDeleteResponse remove(Long memberId, Long inventoryId) {
        Inventory inventory = findOwnedInventory(memberId, inventoryId);
        inventoryRepository.delete(inventory);
        return InventoryDeleteResponse.of(inventoryId);
    }

    public FavoriteUpdateResponse updateFavorite(Long memberId, Long inventoryId, boolean isFavorite) {
        Inventory inventory = findOwnedInventory(memberId, inventoryId);
        inventory.updateFavorite(isFavorite);
        return FavoriteUpdateResponse.from(inventory);
    }

    public AiAnalysisResponse getAiAnalysis(Long memberId, Long inventoryId) {
        Inventory inventory = findOwnedInventory(memberId, inventoryId);
        Member member = findMember(memberId);
        Product product = inventory.getProduct();
        String productName = product.getName();

        List<IngredientAnalysisResponse.IngredientDetail> ingredients = resolveIngredients(productName);
        int score = computeRiskBasedScore(ingredients);
        List<String> ingredientNames = ingredients.stream()
                .map(IngredientAnalysisResponse.IngredientDetail::ingredientName)
                .toList();

        String cacheKey = InventoryAiCacheService.personalizedKey(
                productName, member.getSkinType(), member.getSkinConcerns());
        List<AiAnalysisResponse.AnalysisKeyword> keywords = findCachedKeywords(cacheKey);
        if (keywords == null) {
            PersonalizedAnalysisResult result = personalizedAnalysisAiClient.analyze(
                    productName, ingredientNames, member.getSkinType(), member.getSkinConcerns());
            keywords = toKeywords(result);
            saveCache(cacheKey, keywordsCachePayload(keywords));
        }

        return new AiAnalysisResponse(
                inventory.getId(), productName, publicUrlResolver.resolve(product.getImageUrl()),
                score, keywords, LocalDateTime.now());
    }

    public IngredientAnalysisResponse getIngredientAnalysis(Long memberId, Long inventoryId) {
        Inventory inventory = findOwnedInventory(memberId, inventoryId);
        Product product = inventory.getProduct();
        String productName = product.getName();
        String brand = resolveBrand(product);
        String capacity = productCapacityNormalizer.normalize(productName);

        List<IngredientAnalysisResponse.IngredientDetail> ingredients = resolveIngredients(productName);

        return buildIngredientAnalysisResponse(inventory.getId(), productName, brand, capacity, ingredients);
    }

    /**
     * 전성분/배합목적/위험도를 캐시 → (캐시 미스 시) AI 순으로 조회한다.
     * /ai-analysis와 /ingredients가 동일 캐시 키를 공유하므로, 한쪽에서 이미 조회했다면
     * 다른 쪽에서는 AI를 다시 호출하지 않는다.
     */
    private List<IngredientAnalysisResponse.IngredientDetail> resolveIngredients(String productName) {
        String cacheKey = InventoryAiCacheService.ingredientKey(productName);
        List<IngredientAnalysisResponse.IngredientDetail> cached = findCachedIngredients(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<String> ingredientNames = ingredientAiClient.fetchIngredientNames(productName);
        Map<String, IngredientAiDetail> detailsByName = ingredientAiClient.fetchIngredientDetails(ingredientNames);
        List<IngredientAnalysisResponse.IngredientDetail> ingredients = ingredientNames.stream()
                .map(name -> toIngredientDetail(name, detailsByName.get(name)))
                .toList();
        if (!ingredients.isEmpty()) {
            saveCache(cacheKey, ingredientCachePayload(ingredients));
        }
        return ingredients;
    }

    /**
     * 성분 위험도 비율 기반 결정론적 점수. 낮음/중간/높음 성분 각각에 가중치를 부여해
     * 전체 성분 수로 평균낸다(낮음 비율이 높을수록 고득점). 성분 정보가 전혀 없으면
     * {@link #DEFAULT_SCORE_WITHOUT_INGREDIENTS}를 반환한다.
     */
    private int computeRiskBasedScore(List<IngredientAnalysisResponse.IngredientDetail> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return DEFAULT_SCORE_WITHOUT_INGREDIENTS;
        }
        int low = countByRisk(ingredients, IngredientRiskLevel.LOW);
        int medium = countByRisk(ingredients, IngredientRiskLevel.MEDIUM);
        int high = countByRisk(ingredients, IngredientRiskLevel.HIGH);
        double weightedSum = (double) low * RISK_SCORE_WEIGHT_LOW
                + (double) medium * RISK_SCORE_WEIGHT_MEDIUM
                + (double) high * RISK_SCORE_WEIGHT_HIGH;
        int score = (int) Math.round(weightedSum / ingredients.size());
        return Math.max(0, Math.min(100, score));
    }

    private String resolveBrand(Product product) {
        String existing = product.getBrand();
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        String productName = product.getName();
        String cacheKey = InventoryAiCacheService.brandKey(productName);
        JsonNode cached = findCachedBrand(cacheKey);
        if (cached != null) {
            return InventoryAiJsonSupport.parseBrand(cached);
        }

        String inferred = ingredientAiClient.inferBrand(productName);
        if (inferred != null && !inferred.isBlank()) {
            product.updateBrand(inferred);
            return inferred;
        }
        saveCache(cacheKey, Map.of("brand", ""));
        return null;
    }

    private JsonNode findCachedBrand(String cacheKey) {
        try {
            return inventoryAiCacheService.find(cacheKey).orElse(null);
        } catch (RuntimeException e) {
            log.warn("브랜드 캐시 조회를 건너뜁니다: cacheKey={}, message={}", cacheKey, e.getMessage());
            return null;
        }
    }

    private IngredientAnalysisResponse.IngredientDetail toIngredientDetail(String name, IngredientAiDetail detail) {
        List<IngredientPurposeCategory> purposes = resolvePurposes(detail == null ? null : detail.purposes());
        List<SkinEfficacyTag> efficacyTags = resolveEfficacyTags(detail == null ? null : detail.efficacyTags());
        IngredientRiskLevel riskLevel = IngredientRiskLevel.fromRaw(detail == null ? null : detail.riskLevel());
        return new IngredientAnalysisResponse.IngredientDetail(name, purposes, efficacyTags, riskLevel);
    }

    /**
     * 배합목적은 7개 공식 카테고리로만 제한하며 최소 1개를 보장한다(매칭 실패/빈 응답 시 기본값으로 보정).
     */
    private List<IngredientPurposeCategory> resolvePurposes(List<String> rawPurposes) {
        Set<IngredientPurposeCategory> resolved = toDistinctCategories(rawPurposes);
        if (resolved.isEmpty()) {
            return List.of(DEFAULT_PURPOSE_CATEGORY);
        }
        return List.copyOf(resolved);
    }

    private Set<IngredientPurposeCategory> toDistinctCategories(List<String> rawPurposes) {
        Set<IngredientPurposeCategory> resolved = new LinkedHashSet<>();
        if (rawPurposes == null) {
            return resolved;
        }
        for (String raw : rawPurposes) {
            IngredientPurposeCategory category = IngredientPurposeCategory.fromRaw(raw);
            if (category != null) {
                resolved.add(category);
            }
        }
        return resolved;
    }

    /**
     * 효능 태그는 강제 기본값 없이 0~2개까지 허용한다(둘 다 해당 없으면 빈 배열 유지).
     */
    private List<SkinEfficacyTag> resolveEfficacyTags(List<String> rawEfficacyTags) {
        Set<SkinEfficacyTag> resolved = new LinkedHashSet<>();
        if (rawEfficacyTags != null) {
            for (String raw : rawEfficacyTags) {
                SkinEfficacyTag tag = SkinEfficacyTag.fromRaw(raw);
                if (tag != null) {
                    resolved.add(tag);
                }
            }
        }
        return resolved.stream().limit(MAX_EFFICACY_TAGS).toList();
    }

    private IngredientAnalysisResponse buildIngredientAnalysisResponse(
            Long inventoryId, String productName, String brand, String capacity,
            List<IngredientAnalysisResponse.IngredientDetail> ingredients) {
        List<String> names = ingredients.stream()
                .map(IngredientAnalysisResponse.IngredientDetail::ingredientName)
                .toList();
        int low = countByRisk(ingredients, IngredientRiskLevel.LOW);
        int medium = countByRisk(ingredients, IngredientRiskLevel.MEDIUM);
        int high = countByRisk(ingredients, IngredientRiskLevel.HIGH);
        int caution20Count = cautionIngredientCatalog.countCaution20(names);
        int allergyCount = cautionIngredientCatalog.countAllergens(names);
        return new IngredientAnalysisResponse(
                inventoryId, productName, brand, capacity, ingredients,
                new IngredientAnalysisResponse.RiskDistribution(low, medium, high),
                caution20Count, allergyCount);
    }

    private int countByRisk(
            List<IngredientAnalysisResponse.IngredientDetail> ingredients, IngredientRiskLevel level) {
        return (int) ingredients.stream().filter(detail -> detail.riskLevel() == level).count();
    }

    /**
     * 캐시된 판단 근거 키워드를 조회한다. 캐시 엔트리가 없으면 null(재조회 필요),
     * 있으면 빈 배열이라도 그대로 반환한다(불필요한 재조회 방지).
     */
    private List<AiAnalysisResponse.AnalysisKeyword> findCachedKeywords(String cacheKey) {
        try {
            return inventoryAiCacheService.find(cacheKey)
                    .map(this::keywordsFromCache)
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("맞춤 분석 캐시 조회를 건너뜁니다: cacheKey={}, message={}", cacheKey, e.getMessage());
            return null;
        }
    }

    private List<AiAnalysisResponse.AnalysisKeyword> keywordsFromCache(JsonNode payload) {
        JsonNode keywordsNode = payload.path("keywords");
        if (!keywordsNode.isArray()) {
            return List.of();
        }
        List<AiAnalysisResponse.AnalysisKeyword> keywords = new ArrayList<>();
        keywordsNode.forEach(node -> {
            String keyword = node.path("keyword").asText(null);
            if (keyword == null || keyword.isBlank()) {
                return;
            }
            String reason = node.path("reason").asText(null);
            keywords.add(new AiAnalysisResponse.AnalysisKeyword(keyword, reason));
        });
        return keywords;
    }

    /**
     * AI 맞춤 분석 결과에서 키워드만 채택한다(점수는 성분 위험도 기반으로 별도 산출하므로 폐기).
     * AI가 완전히 실패(null)해도 예외를 던지지 않고 빈 배열로 degrade한다.
     */
    private List<AiAnalysisResponse.AnalysisKeyword> toKeywords(PersonalizedAnalysisResult result) {
        if (result == null || result.keywords() == null) {
            return List.of();
        }
        return result.keywords().stream()
                .filter(keyword -> keyword != null && keyword.keyword() != null && !keyword.keyword().isBlank())
                .map(keyword -> new AiAnalysisResponse.AnalysisKeyword(keyword.keyword(), keyword.reason()))
                .toList();
    }

    private List<IngredientAnalysisResponse.IngredientDetail> findCachedIngredients(String cacheKey) {
        try {
            return inventoryAiCacheService.find(cacheKey)
                    .map(this::ingredientsFromCache)
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("성분 분석 캐시 조회를 건너뜁니다: cacheKey={}, message={}", cacheKey, e.getMessage());
            return null;
        }
    }

    private void saveCache(String cacheKey, Object payload) {
        try {
            inventoryAiCacheService.save(cacheKey, payload);
        } catch (RuntimeException e) {
            log.warn("인벤토리 AI 캐시 저장을 건너뜁니다: cacheKey={}, message={}", cacheKey, e.getMessage());
        }
    }

    private Map<String, Object> keywordsCachePayload(List<AiAnalysisResponse.AnalysisKeyword> keywords) {
        List<Map<String, String>> items = new ArrayList<>();
        for (AiAnalysisResponse.AnalysisKeyword keyword : keywords) {
            Map<String, String> item = new HashMap<>();
            item.put("keyword", keyword.keyword());
            item.put("reason", keyword.reason() == null ? "" : keyword.reason());
            items.add(item);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("keywords", items);
        return payload;
    }

    private Map<String, Object> ingredientCachePayload(
            List<IngredientAnalysisResponse.IngredientDetail> ingredients) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (IngredientAnalysisResponse.IngredientDetail ingredient : ingredients) {
            Map<String, Object> item = new HashMap<>();
            item.put("ingredientName", ingredient.ingredientName());
            item.put("purposes", enumNames(ingredient.purposes()));
            item.put("efficacyTags", enumNames(ingredient.efficacyTags()));
            item.put("riskLevel", ingredient.riskLevel() == null ? null : ingredient.riskLevel().name());
            items.add(item);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("ingredients", items);
        return payload;
    }

    private List<String> enumNames(List<? extends Enum<?>> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(Enum::name).toList();
    }

    private List<IngredientAnalysisResponse.IngredientDetail> ingredientsFromCache(JsonNode payload) {
        JsonNode ingredientsNode = payload.path("ingredients");
        if (!ingredientsNode.isArray() || ingredientsNode.isEmpty()) {
            return null;
        }
        List<IngredientAnalysisResponse.IngredientDetail> ingredients = new ArrayList<>();
        ingredientsNode.forEach(node -> {
            String name = node.path("ingredientName").asText(null);
            if (name == null || name.isBlank()) {
                return;
            }
            List<String> purposeNames = textValues(node.path("purposes"));
            List<String> efficacyTagNames = textValues(node.path("efficacyTags"));
            List<IngredientPurposeCategory> purposes = resolvePurposes(purposeNames);
            List<SkinEfficacyTag> efficacyTags = resolveEfficacyTags(efficacyTagNames);
            IngredientRiskLevel riskLevel = IngredientRiskLevel.fromRaw(node.path("riskLevel").asText(null));
            ingredients.add(new IngredientAnalysisResponse.IngredientDetail(
                    name, purposes, efficacyTags, riskLevel));
        });
        return ingredients.isEmpty() ? null : ingredients;
    }

    private List<String> textValues(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        if (arrayNode.isArray()) {
            arrayNode.forEach(node -> {
                String value = node.asText(null);
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            });
        }
        return values;
    }

    private Inventory findOwnedInventory(Long memberId, Long inventoryId) {
        return inventoryRepository.findByIdAndMemberId(inventoryId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVENTORY_NOT_FOUND));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
