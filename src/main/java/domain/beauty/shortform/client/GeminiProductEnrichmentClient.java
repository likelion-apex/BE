package domain.beauty.shortform.client;

import domain.beauty.config.GeminiProperties;
import domain.beauty.shortform.client.ProductEnrichmentResult.Response;
import domain.beauty.shortform.client.ProductEnrichmentResult.WebSource;
import domain.beauty.shortform.config.ShortformProductEnrichmentProperties;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class GeminiProductEnrichmentClient {

    private static final String API_PATH = "/v1beta/interactions";
    private final RestClient restClient;
    private final GeminiProperties geminiProperties;
    private final ShortformProductEnrichmentProperties properties;
    private final GeminiProductEnrichmentPromptResources promptResources;
    private final ObjectMapper objectMapper;
    private final GeminiModelRouter modelRouter;

    public GeminiProductEnrichmentClient(
            RestClient geminiRestClient,
            GeminiProperties geminiProperties,
            ShortformProductEnrichmentProperties properties,
            GeminiProductEnrichmentPromptResources promptResources,
            ObjectMapper objectMapper,
            GeminiModelRouter modelRouter
    ) {
        this.restClient = geminiRestClient;
        this.geminiProperties = geminiProperties;
        this.properties = properties;
        this.promptResources = promptResources;
        this.objectMapper = objectMapper;
        this.modelRouter = modelRouter;
    }

    public Response enrich(ProductEnrichmentInput input) {
        return enrich(input, true);
    }

    public Response enrichWithoutSearch(ProductEnrichmentInput input) {
        return enrich(input, false);
    }

    private Response enrich(ProductEnrichmentInput input, boolean webSearchEnabled) {
        if (geminiProperties.getApiKey() == null || geminiProperties.getApiKey().isBlank()) {
            throw new CustomException(ErrorCode.SHORTFORM_CONFIGURATION_MISSING,
                    "GEMINI_API_KEY 환경변수가 필요합니다.");
        }

        try {
            Response parsed = modelRouter.route(
                    GeminiRouteProfile.PRODUCT,
                    webSearchEnabled ? "제품 검색" : "제품 지식 보강",
                    model -> executeCandidate(input, webSearchEnabled, model));
            Response response = webSearchEnabled
                    ? parsed
                    : new Response(
                            asKnowledgeEstimates(parsed.result()), parsed.model() + "-knowledge-fallback",
                            parsed.inputTokens(), parsed.outputTokens(), 0, List.of());
            log.info(
                    "Gemini 제품 보강 완료: mode={}, model={}, products={}, webSearchCalls={}, sources={}, inputTokens={}, outputTokens={}",
                    webSearchEnabled ? "google_search" : "model_knowledge",
                    response.model(), input.products().size(), response.webSearchCalls(),
                    response.webSources().size(), response.inputTokens(), response.outputTokens());
            return response;
        } catch (GeminiModelRoutingException exception) {
            if (exception.isConfigurationFailure()) {
                throw new CustomException(ErrorCode.SHORTFORM_CONFIGURATION_MISSING,
                        "Gemini API 키 또는 프로젝트 권한을 확인해 주세요.");
            }
            throw new CustomException(ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE,
                    "Gemini 제품 보강 서비스를 일시적으로 사용할 수 없습니다.");
        } catch (JacksonException exception) {
            throw new CustomException(ErrorCode.SHORTFORM_INVALID_AI_RESPONSE,
                    "Gemini 제품 보강 응답 JSON을 해석할 수 없습니다.");
        }
    }

    private Response executeCandidate(
            ProductEnrichmentInput input,
            boolean webSearchEnabled,
            String model
    ) {
        try {
            String responseBody = restClient.post()
                    .uri(API_PATH)
                    .header("x-goog-api-key", geminiProperties.getApiKey())
                    .body(buildRequest(input, webSearchEnabled, model))
                    .retrieve()
                    .body(String.class);
            Response response = parse(responseBody, model);
            Set<String> requestedKeys = input.products() == null
                    ? Set.of()
                    : input.products().stream()
                            .map(ProductEnrichmentInput.Product::requestKey)
                            .collect(java.util.stream.Collectors.toSet());
            boolean hasRequestedProduct = response.result() != null
                    && response.result().products() != null
                    && response.result().products().stream()
                            .anyMatch(product -> product != null && requestedKeys.contains(product.requestKey()));
            if (!hasRequestedProduct) {
                throw new GeminiCandidateRejectedException("요청한 제품의 보강 결과가 없습니다.");
            }
            return response;
        } catch (JacksonException exception) {
            throw new GeminiCandidateRejectedException("제품 보강 JSON을 해석할 수 없습니다.", exception);
        }
    }

    private ProductEnrichmentResult asKnowledgeEstimates(ProductEnrichmentResult result) {
        if (result == null || result.products() == null) {
            return new ProductEnrichmentResult(List.of());
        }
        List<ProductEnrichmentResult.Product> products = result.products().stream()
                .map(product -> {
                    List<ProductEnrichmentResult.Ingredient> ingredients = product.ingredients() == null
                            ? List.of()
                            : product.ingredients();
                    boolean hasEstimate = product.displayProductName() != null
                            && !product.displayProductName().isBlank()
                            && !ingredients.isEmpty();
                    return new ProductEnrichmentResult.Product(
                            product.requestKey(),
                            product.displayBrand(),
                            product.displayProductName(),
                            product.marketOrVariant(),
                            hasEstimate ? ProductEnrichmentResult.LookupStatus.ESTIMATED : product.lookupStatus(),
                            hasEstimate
                                    ? Math.min(0.84, Math.max(0.60, product.resolutionConfidence()))
                                    : product.resolutionConfidence(),
                            hasEstimate
                                    ? "Gemini 모델 지식 기반 추정입니다. " + textOr(product.notes(), "실제 제품 라벨을 확인해 주세요.")
                                    : product.notes(),
                            List.of(),
                            ingredients
                    );
                })
                .toList();
        return new ProductEnrichmentResult(products);
    }

    private String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Map<String, Object> buildRequest(
            ProductEnrichmentInput input,
            boolean webSearchEnabled,
            String model
    ) throws JacksonException {
        Map<String, Object> request = new LinkedHashMap<>();
		request.put("model", model);
        String systemPrompt = promptResources.systemPrompt();
        if (!webSearchEnabled) {
            systemPrompt += "\n\nGoogle Search 쿼터가 없어 모델 지식으로만 최선 추정합니다. "
                    + "절대 FOUND를 사용하지 말고 모든 입력을 ESTIMATED로 반환하세요. "
                    + "정확 제품을 알면 기억하는 해당 제품 처방을, 정확 제품이 불명확하면 rawProductName 또는 category를 표시명으로 쓰고 "
                    + "그 카테고리의 대표적인 처방을 전성분 추정 목록으로 반드시 한 개 이상 반환하세요. "
                    + "카테고리 대표 처방이면 marketOrVariant와 notes에 제품별 확정값이 아님을 분명히 적으세요. "
                    + "sources는 빈 배열로 두고 불확실성은 notes에 명시하세요.";
        }
        systemPrompt += "\n\n반드시 아래 JSON 계약의 필드명과 구조를 따라 JSON 객체만 반환하세요.\n"
                + promptResources.responseSchema().toString();
        request.put("system_instruction", systemPrompt);
        request.put("input", "다음 JSON의 모든 제품을 "
                + (webSearchEnabled ? "Google Search로 재조사하세요.\n" : "모델 지식으로 보완하세요.\n")
                + objectMapper.writeValueAsString(input));
        if (webSearchEnabled) {
            request.put("tools", List.of(Map.of("type", "google_search")));
        }
        request.put("response_format", Map.of(
                "type", "text",
                "mime_type", "application/json"
        ));
        request.put("generation_config", Map.of(
                "max_output_tokens", properties.getGeminiMaxOutputTokens(),
                "thinking_level", "low"
        ));
        request.put("stream", false);
        request.put("store", false);
        return request;
    }

    private Response parse(String responseBody, String requestedModel) throws JacksonException {
        if (responseBody == null || responseBody.isBlank()) {
            throw new GeminiCandidateRejectedException("제품 보강이 빈 응답을 반환했습니다.");
        }
        JsonNode envelope = objectMapper.readTree(responseBody);
        if (!"completed".equals(envelope.path("status").asString())) {
            throw new GeminiCandidateRejectedException("제품 보강이 완료 상태가 아닙니다.");
        }

        String outputText = null;
        int searchCalls = 0;
        Map<String, WebSource> sources = new LinkedHashMap<>();
        for (JsonNode step : envelope.path("steps")) {
            if ("google_search_call".equals(step.path("type").asString())) {
                searchCalls++;
            }
            if (!"model_output".equals(step.path("type").asString())) {
                continue;
            }
            for (JsonNode content : step.path("content")) {
                if ("text".equals(content.path("type").asString()) && content.path("text").isString()) {
                    outputText = content.path("text").asString();
                }
                for (JsonNode annotation : content.path("annotations")) {
                    String uri = annotation.path("uri").asString(null);
                    if (uri != null && !uri.isBlank()) {
                        sources.putIfAbsent(uri, new WebSource(uri, annotation.path("title").asString(null)));
                    }
                }
            }
        }
        if (outputText == null || outputText.isBlank()) {
            throw new GeminiCandidateRejectedException("제품 보강에 텍스트 결과가 없습니다.");
        }

        ProductEnrichmentResult result = objectMapper.readValue(outputText, ProductEnrichmentResult.class);
        JsonNode usage = envelope.path("usage");
        return new Response(
                result,
                envelope.path("model").asString(requestedModel),
                usage.path("total_input_tokens").asLong(),
                usage.path("total_output_tokens").asLong(),
                searchCalls,
                new ArrayList<>(sources.values())
        );
    }
}
