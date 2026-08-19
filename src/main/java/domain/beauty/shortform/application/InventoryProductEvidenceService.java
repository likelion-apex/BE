package domain.beauty.shortform.application;

import domain.beauty.domain.BeautyRoutineAnalysis.EvidenceSource;
import domain.beauty.domain.BeautyRoutineAnalysis.IdentificationLevel;
import domain.beauty.domain.BeautyRoutineAnalysis.PurposeBasis;
import domain.beauty.domain.BeautyRoutineAnalysis.Step;
import domain.beauty.shortform.application.ShortformAnalysisStateService.InventoryFact;
import domain.beauty.shortform.client.ProductEnrichmentResult;
import domain.beauty.shortform.domain.IngredientVerificationStatus;
import domain.ingredient.domain.ProductIngredient;
import domain.ingredient.repository.ProductIngredientRepository;
import domain.inventory.ProductCategory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class InventoryProductEvidenceService {

    private final ProductIngredientRepository productIngredientRepository;
    private final ShortformProductEnrichmentService enrichmentService;
    private final ShortformProductCategoryResolver categoryResolver;

    public InventoryProductEvidenceService(
            ProductIngredientRepository productIngredientRepository,
            ShortformProductEnrichmentService enrichmentService,
            ShortformProductCategoryResolver categoryResolver
    ) {
        this.productIngredientRepository = productIngredientRepository;
        this.enrichmentService = enrichmentService;
        this.categoryResolver = categoryResolver;
    }

    public Map<Long, InventoryProductEvidence> enrichMatchingCategories(
            List<InventoryFact> inventory,
            Set<ProductCategory> videoCategories
    ) {
        Set<ProductCategory> allowed = videoCategories == null ? Set.of() : videoCategories.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (allowed.isEmpty()) {
            return Map.of();
        }
        List<InventoryFact> candidates = safe(inventory).stream()
                .filter(Objects::nonNull)
                .filter(item -> item.productId() != null)
                .filter(item -> allowed.contains(categoryResolver.parseStored(item.category())))
                .collect(java.util.stream.Collectors.toMap(
                        InventoryFact::productId,
                        item -> item,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ))
                .values().stream().toList();
        return enrich(candidates);
    }

    public Map<Long, InventoryProductEvidence> enrich(List<InventoryFact> products) {
        List<InventoryFact> candidates = safe(products).stream()
                .filter(Objects::nonNull)
                .filter(item -> item.productId() != null)
                .filter(item -> categoryResolver.parseStored(item.category()) != null)
                .collect(java.util.stream.Collectors.toMap(
                        InventoryFact::productId,
                        item -> item,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ))
                .values().stream().toList();
        if (candidates.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<ProductIngredient>> storedByProduct = productIngredientRepository
                .findByProduct_IdIn(candidates.stream().map(InventoryFact::productId).toList())
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        item -> item.getProduct().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        Map<Long, InventoryProductEvidence> result = new LinkedHashMap<>();
        List<InventoryFact> misses = new ArrayList<>();
        for (InventoryFact candidate : candidates) {
            List<ProductIngredient> stored = storedByProduct.getOrDefault(candidate.productId(), List.of());
            if (stored.isEmpty()) {
                misses.add(candidate);
                continue;
            }
            List<ProductEnrichmentResult.Ingredient> ingredients = stored.stream()
                    .sorted(Comparator.comparing(
                            ProductIngredient::getRank,
                            Comparator.nullsLast(Integer::compareTo)))
                    .map(item -> new ProductEnrichmentResult.Ingredient(
                            item.getRank() == null ? 0 : item.getRank(),
                            item.getIngredient().getName(),
                            compact(item.getIngredient().getFunctionTag(), item.getIngredient().getFunctionGroup()),
                            compact(item.getIngredient().getFunctionGroup()),
                            item.getIngredient().getEwgGrade(),
                            false,
                            false
                    ))
                    .toList();
            result.put(candidate.productId(), new InventoryProductEvidence(
                    IngredientVerificationStatus.THIRD_PARTY, ingredients));
        }

        if (!misses.isEmpty()) {
            Map<Integer, InventoryFact> byOrder = new HashMap<>();
            List<Step> lookupSteps = new ArrayList<>();
            int order = 1;
            for (InventoryFact item : misses) {
                byOrder.put(order, item);
                lookupSteps.add(toLookupStep(order, item));
                order++;
            }
            ShortformProductEnrichmentService.BatchResult enriched = enrichmentService.getOrEnrich(lookupSteps);
            enriched.productsByOrder().forEach((index, data) -> {
                InventoryFact item = byOrder.get(index);
                if (item == null) {
                    return;
                }
                result.put(item.productId(), new InventoryProductEvidence(
                        data.ingredientVerificationStatus(), data.ingredients()));
            });
        }
        candidates.forEach(item -> result.putIfAbsent(item.productId(), InventoryProductEvidence.unavailable()));
        return Map.copyOf(result);
    }

    private Step toLookupStep(int order, InventoryFact item) {
        return new Step(
                order,
                "00:00",
                null,
                "얼굴",
                "도포",
                null,
                categoryResolver.parseStored(item.category()).name(),
                PurposeBasis.GENERAL_INFERENCE,
                null,
                IdentificationLevel.EXACT_PRODUCT,
                item.category(),
                item.brand(),
                item.productName(),
                null,
                item.productName(),
                null,
                List.of(EvidenceSource.ON_SCREEN_TEXT),
                "인벤토리에 등록된 제품명",
                1.0
        );
    }

    private List<String> compact(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank() && !result.contains(value.trim())) {
                result.add(value.trim());
            }
        }
        return List.copyOf(result);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
