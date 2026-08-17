package domain.beauty.shortform.client;

import domain.beauty.config.GeminiProperties;
import domain.beauty.shortform.client.ProductEnrichmentResult.Response;
import domain.beauty.shortform.client.ProductEnrichmentResult.WebSource;
import domain.beauty.shortform.config.ShortformProductEnrichmentProperties;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class GeminiProductEnrichmentClient {

    private static final String API_PATH = "/v1beta/interactions";
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final GeminiProperties geminiProperties;
    private final ShortformProductEnrichmentProperties properties;
    private final GeminiProductEnrichmentPromptResources promptResources;
    private final ObjectMapper objectMapper;

    public GeminiProductEnrichmentClient(
            @Qualifier("geminiRestClient") RestClient geminiRestClient,
            GeminiProperties geminiProperties,
            ShortformProductEnrichmentProperties properties,
            GeminiProductEnrichmentPromptResources promptResources,
            ObjectMapper objectMapper
    ) {
        this.restClient = geminiRestClient;
        this.geminiProperties = geminiProperties;
        this.properties = properties;
        this.promptResources = promptResources;
        this.objectMapper = objectMapper;
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
            String responseBody = webSearchEnabled
                    ? executeWithRetry(buildRequest(input, true))
                    : executeOnce(buildRequest(input, false));
            Response parsed = parse(responseBody);
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
        } catch (CustomException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            log.warn("Gemini 제품 웹 보강 HTTP 실패: status={}, model={}",
                    exception.getStatusCode().value(), geminiProperties.getModel());
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED
                    || exception.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new CustomException(ErrorCode.SHORTFORM_CONFIGURATION_MISSING,
                        "Gemini API 키 또는 프로젝트 권한을 확인해 주세요.");
            }
            throw new CustomException(ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE,
                    "Gemini 제품 보강 서비스를 일시적으로 사용할 수 없습니다.");
        } catch (ResourceAccessException exception) {
            throw new CustomException(ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE,
                    "Gemini 제품 보강 응답 시간이 초과되었습니다.");
        } catch (JacksonException exception) {
            throw new CustomException(ErrorCode.SHORTFORM_INVALID_AI_RESPONSE,
                    "Gemini 제품 보강 응답 JSON을 해석할 수 없습니다.");
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

    private String executeOnce(Map<String, Object> request) {
        return restClient.post()
                .uri(API_PATH)
                .header("x-goog-api-key", geminiProperties.getApiKey())
                .body(request)
                .retrieve()
                .body(String.class);
    }

    private String executeWithRetry(Map<String, Object> request) {
        RestClientResponseException lastFailure = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return restClient.post()
                        .uri(API_PATH)
                        .header("x-goog-api-key", geminiProperties.getApiKey())
                        .body(request)
                        .retrieve()
                        .body(String.class);
            } catch (RestClientResponseException exception) {
                lastFailure = exception;
                log.warn("Gemini 제품 웹 보강 HTTP 실패: status={}, model={}, attempt={}",
                        exception.getStatusCode().value(), geminiProperties.getModel(), attempt + 1);
                if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED
                        || exception.getStatusCode() == HttpStatus.FORBIDDEN
                        || quotaExhausted(exception)
                        || (!retryable(exception) || !waitBeforeRetry(exception, attempt))) {
                    throw exception;
                }
            }
        }
        throw lastFailure;
    }

    private boolean retryable(RestClientResponseException exception) {
        return exception.getStatusCode().value() == 429 || exception.getStatusCode().is5xxServerError();
    }

    private boolean quotaExhausted(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        return body != null && body.contains("exceeded your current quota");
    }

    private boolean waitBeforeRetry(RestClientResponseException exception, int attempt) {
        if (attempt >= MAX_ATTEMPTS - 1) {
            return false;
        }
        Duration fallback = Duration.ofSeconds(5L << attempt);
        Duration requested = retryAfter(exception);
        Duration delay = requested == null ? fallback : requested;
        if (delay.compareTo(MAX_RETRY_DELAY) > 0) {
            delay = MAX_RETRY_DELAY;
        }
        try {
            Thread.sleep(delay.toMillis());
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Duration retryAfter(RestClientResponseException exception) {
        if (exception.getResponseHeaders() == null) {
            return null;
        }
        String value = exception.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim())));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, Object> buildRequest(
            ProductEnrichmentInput input,
            boolean webSearchEnabled
    ) throws JacksonException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", geminiProperties.getModel());
        String systemPrompt = promptResources.systemPrompt();
        if (!webSearchEnabled) {
            systemPrompt += "\n\nGoogle Search 쿼터가 없어 모델 지식으로만 최선 추정합니다. "
                    + "절대 FOUND를 사용하지 말고 모든 입력을 ESTIMATED로 반환하세요. "
                    + "정확 제품을 알면 기억하는 해당 제품 처방을, 정확 제품이 불명확하면 rawProductName 또는 category를 표시명으로 쓰고 "
                    + "그 카테고리의 대표적인 처방을 전성분 추정 목록으로 반드시 한 개 이상 반환하세요. "
                    + "카테고리 대표 처방이면 marketOrVariant와 notes에 제품별 확정값이 아님을 분명히 적으세요. "
                    + "sources는 빈 배열로 두고 불확실성은 notes에 명시하세요.";
        }
        request.put("system_instruction", systemPrompt);
        request.put("input", "다음 JSON의 모든 제품을 "
                + (webSearchEnabled ? "Google Search로 재조사하세요.\n" : "모델 지식으로 보완하세요.\n")
                + objectMapper.writeValueAsString(input));
        if (webSearchEnabled) {
            request.put("tools", List.of(Map.of("type", "google_search")));
        }
        request.put("response_format", Map.of(
                "type", "text",
                "mime_type", "application/json",
                "schema", promptResources.responseSchema()
        ));
        request.put("generation_config", Map.of(
                "max_output_tokens", properties.getGeminiMaxOutputTokens(),
                "thinking_level", "low"
        ));
        request.put("stream", false);
        request.put("store", false);
        return request;
    }

    private Response parse(String responseBody) throws JacksonException {
        if (responseBody == null || responseBody.isBlank()) {
            throw new CustomException(ErrorCode.SHORTFORM_INVALID_AI_RESPONSE,
                    "Gemini 제품 보강이 빈 응답을 반환했습니다.");
        }
        JsonNode envelope = objectMapper.readTree(responseBody);
        if (!"completed".equals(envelope.path("status").asString())) {
            throw new CustomException(ErrorCode.SHORTFORM_INVALID_AI_RESPONSE,
                    "Gemini 제품 보강이 완료 상태가 아닙니다.");
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
            throw new CustomException(ErrorCode.SHORTFORM_INVALID_AI_RESPONSE,
                    "Gemini 제품 보강에 텍스트 결과가 없습니다.");
        }

        ProductEnrichmentResult result = objectMapper.readValue(outputText, ProductEnrichmentResult.class);
        JsonNode usage = envelope.path("usage");
        return new Response(
                result,
                envelope.path("model").asString(geminiProperties.getModel()),
                usage.path("total_input_tokens").asLong(),
                usage.path("total_output_tokens").asLong(),
                searchCalls,
                new ArrayList<>(sources.values())
        );
    }
}
