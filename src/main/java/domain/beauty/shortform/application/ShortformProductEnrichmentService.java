package domain.beauty.shortform.application;

import domain.beauty.config.GeminiProperties;
import domain.beauty.domain.BeautyRoutineAnalysis.Step;
import domain.beauty.shortform.client.GeminiProductEnrichmentClient;
import domain.beauty.shortform.client.OpenAiProductEnrichmentClient;
import domain.beauty.shortform.client.ProductEnrichmentInput;
import domain.beauty.shortform.client.ProductEnrichmentResult;
import domain.beauty.shortform.client.ProductEnrichmentResult.LookupStatus;
import domain.beauty.shortform.client.ProductEnrichmentResult.Response;
import domain.beauty.shortform.config.OpenAiRoutineProperties;
import domain.beauty.shortform.config.ShortformProductEnrichmentProperties;
import domain.beauty.shortform.domain.IngredientSourceType;
import domain.beauty.shortform.domain.IngredientVerificationStatus;
import domain.beauty.shortform.domain.ShortformProductEnrichment;
import domain.beauty.shortform.domain.ShortformProductEnrichmentRepository;
import global.exception.CustomException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final OpenAiProductEnrichmentClient openAiClient;
    private final GeminiProductEnrichmentClient geminiClient;
    private final OpenAiRoutineProperties properties;
    private final ShortformProductEnrichmentProperties enrichmentProperties;
    private final GeminiProperties geminiProperties;
    private final ShortformAnalysisJsonMapper jsonMapper;

    public ShortformProductEnrichmentService(
            ShortformProductEnrichmentRepository repository,
            OpenAiProductEnrichmentClient openAiClient,
            GeminiProductEnrichmentClient geminiClient,
            OpenAiRoutineProperties properties,
            ShortformProductEnrichmentProperties enrichmentProperties,
            GeminiProperties geminiProperties,
            ShortformAnalysisJsonMapper jsonMapper
    ) {
        this.repository = repository;
        this.openAiClient = openAiClient;
        this.geminiClient = geminiClient;
        this.properties = properties;
        this.enrichmentProperties = enrichmentProperties;
        this.geminiProperties = geminiProperties;
        this.jsonMapper = jsonMapper;
    }

    public BatchResult getOrEnrich(List<Step> steps) {
        List<RequestedProduct> requested = steps.stream()
                .filter(this::researchable)
                .map(step -> new RequestedProduct(step, cacheKey(step)))
                .toList();
        if (requested.isEmpty()) {
            return BatchResult.empty(properties.getProductModel(), combinedPromptVersion());
        }

        LocalDateTime now = LocalDateTime.now();
        Map<String, ShortformProductEnrichment> cached = enrichmentProperties.isCacheEnabled()
                ? repository.findByCacheKeyIn(requested.stream().map(RequestedProduct::cacheKey).toList())
                        .stream()
                        .filter(entity -> !entity.isExpired(now))
                        .collect(Collectors.toMap(ShortformProductEnrichment::getCacheKey, Function.identity()))
                : Map.of();
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

        log.info("숏폼 제품 웹 보강 캐시: enabled={}, hit={}, miss={}, model={}, fallbackModel={}, promptVersion={}",
                enrichmentProperties.isCacheEnabled(),
                requested.size() - misses.size(), misses.size(), properties.getProductModel(),
                properties.getProductFallbackModel(), combinedPromptVersion());
        if (misses.isEmpty()) {
            return new BatchResult(
                    Map.copyOf(results), properties.getProductModel(), combinedPromptVersion(),
                    0, 0, requested.size(), 0);
        }

        Response primary = emptyResponse(properties.getProductModel());
        boolean primaryUsesFallbackModel = false;
        try {
            primary = openAiClient.enrich(toInput(misses, false));
        } catch (CustomException exception) {
            if (!properties.isProductFallbackEnabled() && !enrichmentProperties.isGeminiFallbackEnabled()) {
                throw exception;
            }
            log.warn("숏폼 제품 기본 모델 실패로 보완 모델을 사용합니다: primary={}, fallback={}, reason={}",
                    properties.getProductModel(), properties.getProductFallbackModel(), exception.getErrorCode());
            if (properties.isProductFallbackEnabled()) {
                try {
                    primary = openAiClient.enrich(toInput(misses, true), properties.getProductFallbackModel());
                    primaryUsesFallbackModel = true;
                } catch (CustomException fallbackException) {
                    if (!enrichmentProperties.isGeminiFallbackEnabled()) {
                        throw fallbackException;
                    }
                    log.warn("OpenAI 제품 보완 모델 실패 후 Gemini로 전환합니다: model={}, reason={}",
                            properties.getProductFallbackModel(), fallbackException.getErrorCode());
                }
            }
        }
        Map<String, ProductEnrichmentResult.Product> enriched = indexValid(primary, misses);
        List<RequestedProduct> fallbackTargets = properties.isProductFallbackEnabled() && !primaryUsesFallbackModel
                ? misses.stream().filter(item -> needsFallback(enriched.get(item.cacheKey()))).toList()
                : List.of();

        long inputTokens = primary.inputTokens();
        long outputTokens = primary.outputTokens();
        String model = primary.model();
        if (!fallbackTargets.isEmpty()) {
            log.info("숏폼 제품 보완 모델 호출: targets={}, model={}",
                    fallbackTargets.size(), properties.getProductFallbackModel());
            try {
                Response fallback = openAiClient.enrich(
                        toInput(fallbackTargets, true), properties.getProductFallbackModel(), 1);
                inputTokens += fallback.inputTokens();
                outputTokens += fallback.outputTokens();
                model = primary.model() + "+" + fallback.model();
                Map<String, ProductEnrichmentResult.Product> fallbackProducts = indexValid(
                        fallback, fallbackTargets);
                for (RequestedProduct target : fallbackTargets) {
                    ProductEnrichmentResult.Product candidate = fallbackProducts.get(target.cacheKey());
                    ProductEnrichmentResult.Product current = enriched.get(target.cacheKey());
                    if (quality(candidate) > quality(current)) {
                        enriched.put(target.cacheKey(), candidate);
                    }
                }
            } catch (CustomException exception) {
                log.warn("숏폼 제품 보완 모델을 건너뜁니다: model={}, targets={}, reason={}",
                        properties.getProductFallbackModel(), fallbackTargets.size(), exception.getErrorCode());
            }
        }

        List<RequestedProduct> geminiTargets = enrichmentProperties.isGeminiFallbackEnabled()
                ? misses.stream().filter(item -> needsGeminiFallback(enriched.get(item.cacheKey()))).toList()
                : List.of();
        if (!geminiTargets.isEmpty()) {
            log.info("Gemini 제품 검색 폴백 호출: targets={}, model={}, promptVersion={}",
                    geminiTargets.size(), geminiProperties.getModel(),
                    enrichmentProperties.getGeminiPromptVersion());
            try {
                Response gemini = geminiClient.enrich(toInput(geminiTargets, true));
                inputTokens += gemini.inputTokens();
                outputTokens += gemini.outputTokens();
                model = appendModel(model, gemini.model());
                Map<String, ProductEnrichmentResult.Product> geminiProducts = indexValid(
                        gemini, geminiTargets);
                for (RequestedProduct target : geminiTargets) {
                    ProductEnrichmentResult.Product candidate = geminiProducts.get(target.cacheKey());
                    ProductEnrichmentResult.Product current = enriched.get(target.cacheKey());
                    if (quality(candidate) > quality(current)) {
                        enriched.put(target.cacheKey(), candidate);
                    }
                }
            } catch (CustomException exception) {
                log.warn("Gemini 제품 검색 실패로 모델 지식 폴백을 사용합니다: model={}, targets={}, reason={}",
                        geminiProperties.getModel(), geminiTargets.size(), exception.getErrorCode());
                try {
                    Response knowledge = geminiClient.enrichWithoutSearch(toInput(geminiTargets, true));
                    inputTokens += knowledge.inputTokens();
                    outputTokens += knowledge.outputTokens();
                    model = appendModel(model, knowledge.model());
                    Map<String, ProductEnrichmentResult.Product> knowledgeProducts = indexValid(
                            knowledge, geminiTargets);
                    for (RequestedProduct target : geminiTargets) {
                        ProductEnrichmentResult.Product candidate = knowledgeProducts.get(target.cacheKey());
                        ProductEnrichmentResult.Product current = enriched.get(target.cacheKey());
                        if (quality(candidate) > quality(current)) {
                            enriched.put(target.cacheKey(), candidate);
                        }
                    }
                } catch (CustomException knowledgeException) {
                    log.warn("Gemini 모델 지식 폴백도 건너뜁니다: model={}, targets={}, reason={}",
                            geminiProperties.getModel(), geminiTargets.size(), knowledgeException.getErrorCode());
                }
            }
        }

        for (RequestedProduct item : misses) {
            ProductEnrichmentResult.Product product = enriched.getOrDefault(
                    item.cacheKey(), fallbackProduct(item));
            save(item.cacheKey(), model, product, inputTokens, outputTokens);
            ProductEnrichmentData data = toData(product);
            results.put(item.step().order(), data);
            log.info("제품 보강 결과: order={}, lookupStatus={}, verification={}, confidence={}, sources={}, ingredients={}",
                    item.step().order(), product.lookupStatus(), data.ingredientVerificationStatus(),
                    data.resolutionConfidence(), data.sources().size(), data.ingredients().size());
        }
        return new BatchResult(
                Map.copyOf(results), model, combinedPromptVersion(),
                inputTokens, outputTokens, requested.size() - misses.size(), misses.size());
    }

    private ProductEnrichmentInput toInput(List<RequestedProduct> items, boolean verificationPass) {
        return new ProductEnrichmentInput(
                verificationPass,
                items.stream().map(item -> new ProductEnrichmentInput.Product(
                        item.cacheKey(),
                        item.step().category(),
                        item.step().brand(),
                        item.step().productName(),
                        item.step().variant(),
                        textOr(item.step().identityEvidenceText(), item.step().evidenceSummary())
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
                indexed.putIfAbsent(product.requestKey(), sanitize(product, response.webSources()));
            }
        }
        return indexed;
    }

    private ProductEnrichmentResult.Product sanitize(
            ProductEnrichmentResult.Product product,
            List<ProductEnrichmentResult.WebSource> webSources
    ) {
        double confidence = Math.max(0, Math.min(1, product.resolutionConfidence()));
        LookupStatus lookupStatus = product.lookupStatus() == null
                ? LookupStatus.NOT_FOUND
                : product.lookupStatus();
        Map<String, ProductEnrichmentResult.WebSource> allowedSources = safe(webSources).stream()
                .filter(Objects::nonNull)
                .filter(source -> canonicalUrl(source.url()) != null)
                .collect(Collectors.toMap(
                        source -> canonicalUrl(source.url()),
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        Set<String> seen = new LinkedHashSet<>();
        List<ProductEnrichmentResult.Source> sources = safe(product.sources()).stream()
                .filter(Objects::nonNull)
                .filter(source -> canonicalUrl(source.url()) != null)
                .filter(source -> allowedSources.containsKey(canonicalUrl(source.url())))
                .filter(source -> seen.add(canonicalUrl(source.url())))
                .limit(8)
                .map(source -> {
                    ProductEnrichmentResult.WebSource webSource = allowedSources.get(canonicalUrl(source.url()));
                    return new ProductEnrichmentResult.Source(
                            webSource.url(),
                            textOr(trimToNull(source.title()), trimToNull(webSource.title())),
                            source.sourceType() == null ? IngredientSourceType.OTHER : source.sourceType());
                })
                .toList();
        List<ProductEnrichmentResult.Ingredient> ingredients = safe(product.ingredients()).stream()
                .filter(Objects::nonNull)
                .filter(item -> item.name() != null && !item.name().isBlank())
                .sorted(Comparator.comparingInt(ProductEnrichmentResult.Ingredient::order))
                .limit(200)
                .toList();
        String displayProductName = trimToNull(product.displayProductName());
        boolean verified = lookupStatus == LookupStatus.FOUND && confidence >= RESOLUTION_THRESHOLD;
        boolean estimated = lookupStatus == LookupStatus.ESTIMATED
                && confidence >= enrichmentProperties.getEstimatedResolutionThreshold();
        if ((!verified && !estimated)
                || displayProductName == null
                || (verified && sources.isEmpty())) {
            ingredients = List.of();
        }
        return new ProductEnrichmentResult.Product(
                product.requestKey(),
                trimToNull(product.displayBrand()),
                displayProductName,
                trimToNull(product.marketOrVariant()),
                lookupStatus,
                confidence,
                trimToNull(product.notes()),
                sources,
                ingredients
        );
    }

    private boolean needsFallback(ProductEnrichmentResult.Product product) {
        return product == null || !toData(product).hasVerifiedIngredients();
    }

    private boolean needsGeminiFallback(ProductEnrichmentResult.Product product) {
        if (product == null) {
            return true;
        }
        IngredientVerificationStatus status = toData(product).ingredientVerificationStatus();
        return status == IngredientVerificationStatus.UNVERIFIED
                || status == IngredientVerificationStatus.AMBIGUOUS
                || status == IngredientVerificationStatus.ESTIMATED;
    }

    private int quality(ProductEnrichmentResult.Product product) {
        if (product == null) {
            return -1;
        }
        ProductEnrichmentData data = toData(product);
        int verification = switch (data.ingredientVerificationStatus()) {
            case OFFICIAL -> 500;
            case CORROBORATED -> 400;
            case THIRD_PARTY -> 300;
            case ESTIMATED -> 200;
            case AMBIGUOUS -> 100;
            case UNVERIFIED -> 0;
        };
        int ingredientCount = Math.min(200, data.ingredients().size());
        return verification + ingredientCount + (int) Math.round(data.resolutionConfidence() * 10);
    }

    private ProductEnrichmentResult.Product fallbackProduct(RequestedProduct item) {
        return new ProductEnrichmentResult.Product(
                item.cacheKey(),
                item.step().brand(),
                item.step().productName(),
                item.step().variant(),
                LookupStatus.NOT_FOUND,
                0,
                "웹 검색에서 정확한 제품과 전체 성분 근거를 확인하지 못했습니다.",
                List.of(),
                List.of()
        );
    }

    private void save(
            String cacheKey,
            String model,
            ProductEnrichmentResult.Product product,
            long inputTokens,
            long outputTokens
    ) {
        if (!enrichmentProperties.isCacheEnabled()) {
            return;
        }
        ProductEnrichmentData data = toData(product);
        LocalDateTime expiresAt = LocalDateTime.now().plus(
                data.hasVerifiedIngredients()
                        ? properties.getProductCacheTtl()
                        : properties.getProductNegativeCacheTtl());
        ShortformProductEnrichment entity = repository.findByCacheKey(cacheKey).orElse(null);
        if (entity == null) {
            entity = new ShortformProductEnrichment(
                    cacheKey,
                    model,
                    combinedPromptVersion(),
                    jsonMapper.write(product),
                    inputTokens,
                    outputTokens,
                    expiresAt
            );
        } else {
            entity.refresh(
                    model,
                    combinedPromptVersion(),
                    jsonMapper.write(product),
                    inputTokens,
                    outputTokens,
                    expiresAt
            );
        }
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
        List<ProductEnrichmentResult.Source> sources = safe(product.sources());
        List<ProductEnrichmentResult.Ingredient> ingredients = safe(product.ingredients());
        IngredientVerificationStatus verificationStatus = verificationStatus(product, sources, ingredients);
        return new ProductEnrichmentData(
                trimToNull(product.displayBrand()),
                trimToNull(product.displayProductName()),
                trimToNull(product.marketOrVariant()),
                Math.max(0, Math.min(1, product.resolutionConfidence())),
                verificationStatus,
                List.copyOf(sources),
                verificationStatus.isAvailable() ? List.copyOf(ingredients) : List.of()
        );
    }

    private IngredientVerificationStatus verificationStatus(
            ProductEnrichmentResult.Product product,
            List<ProductEnrichmentResult.Source> sources,
            List<ProductEnrichmentResult.Ingredient> ingredients
    ) {
        if (product.lookupStatus() == LookupStatus.AMBIGUOUS) {
            return IngredientVerificationStatus.AMBIGUOUS;
        }
        if (product.lookupStatus() == LookupStatus.ESTIMATED) {
            return product.resolutionConfidence() >= enrichmentProperties.getEstimatedResolutionThreshold()
                    && !ingredients.isEmpty()
                    ? IngredientVerificationStatus.ESTIMATED
                    : IngredientVerificationStatus.UNVERIFIED;
        }
        if (product.lookupStatus() != LookupStatus.FOUND
                || product.resolutionConfidence() < RESOLUTION_THRESHOLD
                || sources.isEmpty()
                || ingredients.isEmpty()) {
            return IngredientVerificationStatus.UNVERIFIED;
        }
        if (sources.stream().anyMatch(source -> source.sourceType() == IngredientSourceType.OFFICIAL)) {
            return IngredientVerificationStatus.OFFICIAL;
        }
        if (sources.size() >= 2) {
            return IngredientVerificationStatus.CORROBORATED;
        }
        return IngredientVerificationStatus.THIRD_PARTY;
    }

    private boolean researchable(Step step) {
        return step != null && ((step.category() != null && !step.category().isBlank())
                || (step.productName() != null && !step.productName().isBlank())
                || (step.evidenceSummary() != null && !step.evidenceSummary().isBlank()));
    }

    private String cacheKey(Step step) {
        String source = String.join("#",
                properties.getProductModel(),
                properties.getProductFallbackModel(),
                properties.getProductPromptVersion(),
                geminiProperties.getModel(),
                enrichmentProperties.getGeminiPromptVersion(),
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

    private String canonicalUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(value.trim()).normalize();
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                return null;
            }
            URI canonical = new URI(
                    uri.getScheme().toLowerCase(),
                    uri.getUserInfo(),
                    uri.getHost().toLowerCase(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    null
            );
            String result = canonical.toString();
            return result.endsWith("/") ? result.substring(0, result.length() - 1) : result;
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Response emptyResponse(String model) {
        return new Response(new ProductEnrichmentResult(List.of()), model, 0, 0, 0, List.of());
    }

    private String appendModel(String current, String next) {
        if (next == null || next.isBlank()) {
            return current;
        }
        if (current == null || current.isBlank()) {
            return next;
        }
        return current + "+" + next;
    }

    private String combinedPromptVersion() {
        return properties.getProductPromptVersion()
                + "+gemini-" + enrichmentProperties.getGeminiPromptVersion();
    }

    private <T> List<T> safe(List<T> value) {
        return value == null ? List.of() : value;
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
