package domain.beauty.shortform.application;

import domain.beauty.domain.BeautyRoutineAnalysis.IdentificationLevel;
import domain.beauty.domain.BeautyRoutineAnalysis.Step;
import domain.beauty.shortform.client.OpenAiProductEnrichmentClient;
import domain.beauty.shortform.client.ProductEnrichmentInput;
import domain.beauty.shortform.client.ProductEnrichmentResult;
import domain.beauty.shortform.client.ProductEnrichmentResult.Response;
import domain.beauty.shortform.config.OpenAiRoutineProperties;
import domain.beauty.shortform.domain.ShortformProductEnrichment;
import domain.beauty.shortform.domain.ShortformProductEnrichmentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ShortformProductEnrichmentService {

    private static final double RESOLUTION_THRESHOLD = 0.85;

    private final ShortformProductEnrichmentRepository repository;
    private final OpenAiProductEnrichmentClient client;
    private final OpenAiRoutineProperties properties;
    private final ShortformAnalysisJsonMapper jsonMapper;

    public ShortformProductEnrichmentService(
            ShortformProductEnrichmentRepository repository,
            OpenAiProductEnrichmentClient client,
            OpenAiRoutineProperties properties,
            ShortformAnalysisJsonMapper jsonMapper
    ) {
        this.repository = repository;
        this.client = client;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    public BatchResult getOrEnrich(List<Step> steps) {
        List<RequestedProduct> requested = steps.stream()
                .filter(this::eligible)
                .map(step -> new RequestedProduct(step, cacheKey(step)))
                .toList();
        if (requested.isEmpty()) {
            return BatchResult.empty(properties.getProductModel(), properties.getProductPromptVersion());
        }

        Map<String, ShortformProductEnrichment> cached = repository
                .findByCacheKeyIn(requested.stream().map(RequestedProduct::cacheKey).toList())
                .stream()
                .collect(Collectors.toMap(ShortformProductEnrichment::getCacheKey, Function.identity()));
        Map<Integer, ProductEnrichmentData> results = new LinkedHashMap<>();
        List<RequestedProduct> misses = new ArrayList<>();
        for (RequestedProduct item : requested) {
            ShortformProductEnrichment entity = cached.get(item.cacheKey());
            if (entity == null) {
                misses.add(item);
            } else {
                results.put(item.step().order(), toData(read(entity)));
            }
        }

        log.info("숏폼 제품 보강 캐시: hit={}, miss={}, model={}, promptVersion={}",
                requested.size() - misses.size(), misses.size(),
                properties.getProductModel(), properties.getProductPromptVersion());
        if (misses.isEmpty()) {
            return new BatchResult(
                    Map.copyOf(results), properties.getProductModel(), properties.getProductPromptVersion(),
                    0, 0, requested.size(), 0);
        }

        Response initial = client.enrich(toInput(misses, false));
        Map<String, ProductEnrichmentResult.Product> enriched = indexValid(initial, misses);
        List<RequestedProduct> repairTargets = misses.stream()
                .filter(item -> needsRepair(enriched.get(item.cacheKey())))
                .toList();

        long inputTokens = initial.inputTokens();
        long outputTokens = initial.outputTokens();
        String model = initial.model();
        if (!repairTargets.isEmpty()) {
            Response repair = client.enrich(toInput(repairTargets, true));
            inputTokens += repair.inputTokens();
            outputTokens += repair.outputTokens();
            model = repair.model();
            Map<String, ProductEnrichmentResult.Product> repaired = indexValid(repair, repairTargets);
            for (RequestedProduct target : repairTargets) {
                ProductEnrichmentResult.Product value = repaired.get(target.cacheKey());
                if (value != null && value.ingredients() != null && !value.ingredients().isEmpty()) {
                    enriched.put(target.cacheKey(), value);
                }
            }
        }

        for (RequestedProduct item : misses) {
            ProductEnrichmentResult.Product product = enriched.getOrDefault(
                    item.cacheKey(), fallbackProduct(item));
            save(item.cacheKey(), model, product, inputTokens, outputTokens);
            results.put(item.step().order(), toData(product));
        }
        return new BatchResult(
                Map.copyOf(results), model, properties.getProductPromptVersion(),
                inputTokens, outputTokens, requested.size() - misses.size(), misses.size());
    }

    private ProductEnrichmentInput toInput(List<RequestedProduct> items, boolean repair) {
        return new ProductEnrichmentInput(
                repair,
                items.stream().map(item -> new ProductEnrichmentInput.Product(
                        item.cacheKey(),
                        item.step().category(),
                        item.step().brand(),
                        item.step().productName(),
                        item.step().variant(),
                        item.step().identityEvidenceText()
                )).toList()
        );
    }

    private Map<String, ProductEnrichmentResult.Product> indexValid(
            Response response,
            List<RequestedProduct> requested
    ) {
        Map<String, RequestedProduct> allowed = requested.stream()
                .collect(Collectors.toMap(RequestedProduct::cacheKey, Function.identity()));
        Map<String, ProductEnrichmentResult.Product> indexed = new HashMap<>();
        List<ProductEnrichmentResult.Product> products = response.result() == null
                || response.result().products() == null ? List.of() : response.result().products();
        for (ProductEnrichmentResult.Product product : products) {
            if (product != null && allowed.containsKey(product.requestKey())) {
                indexed.putIfAbsent(product.requestKey(), sanitize(product));
            }
        }
        return indexed;
    }

    private ProductEnrichmentResult.Product sanitize(ProductEnrichmentResult.Product product) {
        double confidence = Math.max(0, Math.min(1, product.resolutionConfidence()));
        List<ProductEnrichmentResult.Ingredient> ingredients = product.ingredients() == null
                ? List.of()
                : product.ingredients().stream()
                        .filter(Objects::nonNull)
                        .filter(item -> item.name() != null && !item.name().isBlank())
                        .sorted(java.util.Comparator.comparingInt(ProductEnrichmentResult.Ingredient::order))
                        .limit(200)
                        .toList();
        String displayProductName = trimToNull(product.displayProductName());
        if (confidence < RESOLUTION_THRESHOLD || displayProductName == null) {
            ingredients = List.of();
        }
        return new ProductEnrichmentResult.Product(
                product.requestKey(),
                trimToNull(product.displayBrand()),
                displayProductName,
                confidence,
                ingredients
        );
    }

    private boolean needsRepair(ProductEnrichmentResult.Product product) {
        return product != null
                && product.resolutionConfidence() >= RESOLUTION_THRESHOLD
                && product.displayProductName() != null
                && (product.ingredients() == null || product.ingredients().isEmpty());
    }

    private ProductEnrichmentResult.Product fallbackProduct(RequestedProduct item) {
        return new ProductEnrichmentResult.Product(
                item.cacheKey(), item.step().brand(), item.step().productName(), 0, List.of());
    }

    private void save(
            String cacheKey,
            String model,
            ProductEnrichmentResult.Product product,
            long inputTokens,
            long outputTokens
    ) {
        ShortformProductEnrichment entity = new ShortformProductEnrichment(
                cacheKey,
                model,
                properties.getProductPromptVersion(),
                jsonMapper.write(product),
                inputTokens,
                outputTokens
        );
        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            if (repository.findByCacheKey(cacheKey).isEmpty()) {
                throw exception;
            }
        }
    }

    private ProductEnrichmentResult.Product read(ShortformProductEnrichment entity) {
        return jsonMapper.read(entity.getResultJson(), ProductEnrichmentResult.Product.class);
    }

    private ProductEnrichmentData toData(ProductEnrichmentResult.Product product) {
        return new ProductEnrichmentData(
                trimToNull(product.displayBrand()),
                trimToNull(product.displayProductName()),
                Math.max(0, Math.min(1, product.resolutionConfidence())),
                product.ingredients() == null ? List.of() : List.copyOf(product.ingredients())
        );
    }

    private boolean eligible(Step step) {
        return step.identificationLevel() == IdentificationLevel.EXACT_PRODUCT
                && step.confidence() >= RESOLUTION_THRESHOLD
                && step.productName() != null
                && !step.productName().isBlank();
    }

    private String cacheKey(Step step) {
        String source = String.join("#",
                properties.getProductModel(),
                properties.getProductPromptVersion(),
                nullToEmpty(step.category()),
                nullToEmpty(step.brand()),
                nullToEmpty(step.productName()),
                nullToEmpty(step.variant()),
                nullToEmpty(step.identityEvidenceText()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record RequestedProduct(Step step, String cacheKey) {
    }

    public record BatchResult(
            Map<Integer, ProductEnrichmentData> productsByOrder,
            String model,
            String promptVersion,
            long inputTokens,
            long outputTokens,
            int cacheHits,
            int cacheMisses
    ) {
        static BatchResult empty(String model, String promptVersion) {
            return new BatchResult(Map.of(), model, promptVersion, 0, 0, 0, 0);
        }
    }
}
