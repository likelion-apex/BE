package domain.beauty.shortform.client;

import domain.beauty.shortform.client.ProductEnrichmentResult.Response;
import domain.beauty.shortform.client.ProductEnrichmentResult.WebSource;
import domain.beauty.shortform.config.OpenAiRoutineProperties;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class OpenAiProductEnrichmentClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final OpenAiRoutineProperties properties;
    private final OpenAiProductEnrichmentPromptResources promptResources;
    private final ObjectMapper objectMapper;
    private final Semaphore requestPermit = new Semaphore(1, true);

    public OpenAiProductEnrichmentClient(
            @Qualifier("shortformOpenAiRestClient") RestClient restClient,
            OpenAiRoutineProperties properties,
            OpenAiProductEnrichmentPromptResources promptResources,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.promptResources = promptResources;
        this.objectMapper = objectMapper;
    }

    public Response enrich(ProductEnrichmentInput input) {
        return enrich(input, properties.getProductModel());
    }

    public Response enrich(ProductEnrichmentInput input, String model) {
        return enrich(input, model, MAX_ATTEMPTS);
    }

    public Response enrich(ProductEnrichmentInput input, String model, int maxAttempts) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new CustomException(ErrorCode.SHORTFORM_CONFIGURATION_MISSING,
                    "OPENAI_API_KEY 환경변수가 필요합니다.");
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("reasoning", Map.of("effort", properties.getProductReasoningEffort()));
            body.put("tools", List.of(Map.of("type", "web_search")));
            body.put("tool_choice", "auto");
            body.put("include", List.of("web_search_call.action.sources"));
            body.put("max_output_tokens", properties.getProductMaxOutputTokens());
            body.put("store", false);
            body.put("instructions", promptResources.systemPrompt());
            body.put("input", "다음 JSON의 제품만 조사하고 검증하세요.\n"
                    + objectMapper.writeValueAsString(input));
            body.put("text", Map.of("format", Map.of(
                    "type", "json_schema",
                    "name", "shortform_product_enrichment",
                    "strict", true,
                    "schema", promptResources.responseSchema()
            )));

            JsonNode envelope = execute(body, model, Math.max(1, maxAttempts));
            String content = outputText(envelope);
            if (content == null || content.isBlank()) {
                throw new CustomException(ErrorCode.SHORTFORM_INVALID_AI_RESPONSE);
            }
            ProductEnrichmentResult result = objectMapper.readValue(content, ProductEnrichmentResult.class);
            JsonNode usage = envelope.path("usage");
            String responseModel = envelope.path("model").asText(model);
            long inputTokens = usage.path("input_tokens").asLong();
            long outputTokens = usage.path("output_tokens").asLong();
            int webSearchCalls = webSearchCalls(envelope);
            List<WebSource> webSources = webSources(envelope);
            log.info(
                    "OpenAI 제품 웹 보강 완료: model={}, products={}, webSearchCalls={}, sources={}, inputTokens={}, outputTokens={}",
                    responseModel,
                    input.products().size(),
                    webSearchCalls,
                    webSources.size(),
                    inputTokens,
                    outputTokens
            );
            return new Response(
                    result, responseModel, inputTokens, outputTokens, webSearchCalls, webSources);
        } catch (CustomException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new CustomException(ErrorCode.SHORTFORM_INVALID_AI_RESPONSE,
                    "OpenAI 제품 웹 보강 응답 JSON을 해석할 수 없습니다.");
        }
    }

    private JsonNode execute(Map<String, Object> body, String model, int maxAttempts) {
        boolean acquired = false;
        try {
            requestPermit.acquire();
            acquired = true;
            return executeWithRetry(body, model, maxAttempts);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE,
                    "OpenAI 제품 보강 대기 중 요청이 중단되었습니다.");
        } finally {
            if (acquired) {
                requestPermit.release();
            }
        }
    }

    private JsonNode executeWithRetry(Map<String, Object> body, String model, int maxAttempts) {
        RestClientException lastFailure = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                JsonNode response = restClient.post()
                        .uri(properties.getProductApiUrl())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                        .headers(headers -> {
                            if (properties.getOrganizationId() != null
                                    && !properties.getOrganizationId().isBlank()) {
                                headers.set("OpenAI-Organization", properties.getOrganizationId());
                            }
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
                log.info("OpenAI 제품 웹 보강 HTTP 성공: status=200, model={}", model);
                return response;
            } catch (RestClientResponseException exception) {
                lastFailure = exception;
                log.warn("OpenAI 제품 웹 보강 HTTP 실패: status={}, model={}, attempt={}",
                        exception.getStatusCode().value(), model, attempt + 1);
                if (!retryable(exception)
                        || !waitBeforeRetry(attempt, maxAttempts, retryAfter(exception))) {
                    break;
                }
            } catch (RestClientException exception) {
                lastFailure = exception;
                log.warn("OpenAI 제품 웹 보강 연결 실패: model={}, attempt={}", model, attempt + 1);
                if (!waitBeforeRetry(attempt, maxAttempts, null)) {
                    break;
                }
            }
        }
        throw new CustomException(
                ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE,
                lastFailure == null
                        ? "OpenAI 제품 보강 요청에 실패했습니다."
                        : "OpenAI 제품 보강 서비스를 일시적으로 사용할 수 없습니다."
        );
    }

    private String outputText(JsonNode envelope) {
        for (JsonNode item : envelope.path("output")) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    return content.path("text").asText(null);
                }
            }
        }
        return null;
    }

    private int webSearchCalls(JsonNode envelope) {
        int count = 0;
        for (JsonNode item : envelope.path("output")) {
            count += "web_search_call".equals(item.path("type").asText()) ? 1 : 0;
        }
        return count;
    }

    private List<WebSource> webSources(JsonNode envelope) {
        Map<String, WebSource> unique = new LinkedHashMap<>();
        for (JsonNode item : envelope.path("output")) {
            if (!"web_search_call".equals(item.path("type").asText())) {
                continue;
            }
            for (JsonNode source : item.path("action").path("sources")) {
                String url = source.path("url").asText(null);
                if (url != null && !url.isBlank()) {
                    unique.putIfAbsent(url, new WebSource(url, source.path("title").asText(null)));
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    private boolean retryable(RestClientResponseException exception) {
        return exception.getStatusCode().value() == 429
                || exception.getStatusCode().is5xxServerError();
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

    private boolean waitBeforeRetry(int attempt, int maxAttempts, Duration serverDelay) {
        if (attempt >= maxAttempts - 1) {
            return false;
        }
        Duration fallback = Duration.ofMillis(500L << attempt);
        Duration requested = serverDelay == null ? fallback : serverDelay;
        Duration delay = requested.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : requested;
        try {
            Thread.sleep(delay.toMillis());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
