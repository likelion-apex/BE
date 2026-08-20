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
import domain.inventory.client.OpenAiPersonalizedAnalysisClient;
import domain.inventory.client.PersonalizedAnalysisResult;
import domain.inventory.dto.request.InventoryCreateRequest;
import domain.inventory.dto.response.AiAnalysisResponse;
import domain.inventory.dto.response.FavoriteInventoryResponse;
import domain.inventory.dto.response.FavoriteUpdateResponse;
import domain.inventory.dto.response.IngredientAnalysisResponse;
import domain.inventory.dto.response.IngredientRiskLevel;
import domain.inventory.dto.response.InventoryCreateResponse;
import domain.inventory.dto.response.InventoryDeleteResponse;
import domain.inventory.dto.response.InventoryListResponse;
import domain.member.Member;
import domain.member.MemberRepository;
import global.exception.CustomException;
import global.exception.ErrorCode;
import global.util.PublicUrlResolver;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final String DEFAULT_PURPOSE = "배합목적 확인 필요";

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
        String productName = inventory.getProduct().getName();
        String cacheKey = InventoryAiCacheService.personalizedKey(
                productName, member.getSkinType(), member.getSkinConcerns());
        PersonalizedAnalysisResult cached = findCachedAnalysis(cacheKey);
        if (cached != null) {
            return toAiResponse(inventory.getId(), productName, cached);
        }

        List<String> ingredientNames = cachedIngredientNames(productName);
        PersonalizedAnalysisResult result = personalizedAnalysisAiClient.analyze(
                productName, ingredientNames, member.getSkinType(), member.getSkinConcerns());
        if (result == null) {
            throw new CustomException(ErrorCode.AI_ANALYSIS_FAILED);
        }
        saveCache(cacheKey, personalizedCachePayload(result));
        return toAiResponse(inventory.getId(), productName, result);
    }

    public IngredientAnalysisResponse getIngredientAnalysis(Long memberId, Long inventoryId) {
        Inventory inventory = findOwnedInventory(memberId, inventoryId);
        Product product = inventory.getProduct();
        String productName = product.getName();
        String brand = resolveBrand(product);
        String capacity = productCapacityNormalizer.normalize(productName);

        String cacheKey = InventoryAiCacheService.ingredientKey(productName);
        List<IngredientAnalysisResponse.IngredientDetail> cached = findCachedIngredients(cacheKey);
        List<IngredientAnalysisResponse.IngredientDetail> ingredients;
        if (cached != null) {
            ingredients = cached;
        } else {
            List<String> ingredientNames = ingredientAiClient.fetchIngredientNames(productName);
            Map<String, IngredientAiDetail> detailsByName = ingredientAiClient.fetchIngredientDetails(ingredientNames);
            ingredients = ingredientNames.stream()
                    .map(name -> toIngredientDetail(name, detailsByName.get(name)))
                    .toList();
            if (!ingredients.isEmpty()) {
                saveCache(cacheKey, ingredientCachePayload(ingredients));
            }
        }

        return buildIngredientAnalysisResponse(inventory.getId(), productName, brand, capacity, ingredients);
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
        List<String> purposes = purposesOrFallback(detail == null ? null : detail.purposes());
        IngredientRiskLevel riskLevel = IngredientRiskLevel.fromRaw(detail == null ? null : detail.riskLevel());
        return new IngredientAnalysisResponse.IngredientDetail(name, purposes, riskLevel);
    }

    private List<String> purposesOrFallback(List<String> purposes) {
        if (purposes == null || purposes.isEmpty()) {
            return List.of(DEFAULT_PURPOSE);
        }
        return purposes;
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

    private List<String> cachedIngredientNames(String productName) {
        List<IngredientAnalysisResponse.IngredientDetail> cached =
                findCachedIngredients(InventoryAiCacheService.ingredientKey(productName));
        if (cached == null || cached.isEmpty()) {
            return List.of();
        }
        return cached.stream()
                .map(IngredientAnalysisResponse.IngredientDetail::ingredientName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
    }

    private PersonalizedAnalysisResult findCachedAnalysis(String cacheKey) {
        try {
            return inventoryAiCacheService.find(cacheKey)
                    .map(OpenAiPersonalizedAnalysisClient::parseResult)
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("맞춤 분석 캐시 조회를 건너뜁니다: cacheKey={}, message={}", cacheKey, e.getMessage());
            return null;
        }
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

    private Map<String, Object> personalizedCachePayload(PersonalizedAnalysisResult result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("score", result.score());
        List<Map<String, String>> keywords = new ArrayList<>();
        List<PersonalizedAnalysisResult.Keyword> source =
                result.keywords() == null ? List.of() : result.keywords();
        for (PersonalizedAnalysisResult.Keyword keyword : source) {
            if (keyword == null || keyword.keyword() == null || keyword.keyword().isBlank()) {
                continue;
            }
            Map<String, String> item = new HashMap<>();
            item.put("keyword", keyword.keyword());
            item.put("reason", keyword.reason() == null ? "" : keyword.reason());
            keywords.add(item);
        }
        payload.put("keywords", keywords);
        return payload;
    }

    private Map<String, Object> ingredientCachePayload(
            List<IngredientAnalysisResponse.IngredientDetail> ingredients) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (IngredientAnalysisResponse.IngredientDetail ingredient : ingredients) {
            Map<String, Object> item = new HashMap<>();
            item.put("ingredientName", ingredient.ingredientName());
            item.put("purposes", ingredient.purposes() == null ? List.of() : ingredient.purposes());
            item.put("riskLevel", ingredient.riskLevel() == null ? null : ingredient.riskLevel().name());
            items.add(item);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("ingredients", items);
        return payload;
    }

    private AiAnalysisResponse toAiResponse(Long inventoryId, String productName, PersonalizedAnalysisResult result) {
        List<PersonalizedAnalysisResult.Keyword> source =
                result.keywords() == null ? List.of() : result.keywords();
        List<AiAnalysisResponse.AnalysisKeyword> keywords = source.stream()
                .filter(keyword -> keyword != null && keyword.keyword() != null && !keyword.keyword().isBlank())
                .map(keyword -> new AiAnalysisResponse.AnalysisKeyword(keyword.keyword(), keyword.reason()))
                .toList();
        return new AiAnalysisResponse(inventoryId, productName, result.score(), keywords, LocalDateTime.now());
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
            List<String> purposes = new ArrayList<>();
            JsonNode purposesNode = node.path("purposes");
            if (purposesNode.isArray()) {
                purposesNode.forEach(purpose -> {
                    String value = purpose.asText(null);
                    if (value != null && !value.isBlank()) {
                        purposes.add(value);
                    }
                });
            }
            IngredientRiskLevel riskLevel = IngredientRiskLevel.fromRaw(node.path("riskLevel").asText(null));
            ingredients.add(new IngredientAnalysisResponse.IngredientDetail(name, purposesOrFallback(purposes), riskLevel));
        });
        return ingredients.isEmpty() ? null : ingredients;
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
