package domain.beauty.shortform.client;

import domain.beauty.shortform.client.ProductEnrichmentResult.Response;
import domain.beauty.shortform.config.OpenAiRoutineProperties;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final RestClient restClient;
    private final OpenAiRoutineProperties properties;
    private final OpenAiProductEnrichmentPromptResources promptResources;
    private final ObjectMapper objectMapper;

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
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new CustomException(ErrorCode.SHORTFORM_CONFIGURATION_MISSING,
                    "OPENAI_API_KEY 환경변수가 필요합니다.");
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", properties.getProductModel());
            body.put("temperature", 0);
            body.put("max_tokens", properties.getProductMaxOutputTokens());
            body.put("response_format", Map.of(
                    "type", "json_schema",
                    "json_schema", Map.of(
                            "name", "shortform_product_enrichment",
                            "strict", true,
                            "schema", promptResources.responseSchema()
                    )
            ));
            body.put("messages", List.of(
                    Map.of("role", "system", "content", promptResources.systemPrompt()),
                    Map.of("role", "user", "content",
                            "다음 JSON의 제품만 보강하세요. repairMissingIngredients가 true이면 빈 전성분을 한 번 보정하는 요청입니다.\n"
                                    + objectMapper.writeValueAsString(input))
            ));

            JsonNode envelope = execute(body);
            String content = envelope.path("choices").path(0).path("message").path("content").stringValue(null);
            if (content == null || content.isBlank()) {
                throw new CustomException(ErrorCode.SHORTFORM_INVALID_AI_RESPONSE);
            }
            ProductEnrichmentResult result = objectMapper.readValue(content, ProductEnrichmentResult.class);
            JsonNode usage = envelope.path("usage");
            String model = envelope.path("model").stringValue(properties.getProductModel());
            long inputTokens = usage.path("prompt_tokens").asLong();
            long outputTokens = usage.path("completion_tokens").asLong();
            log.info("OpenAI 제품 보강 완료: model={}, products={}, inputTokens={}, outputTokens={}",
                    model, input.products().size(), inputTokens, outputTokens);
            return new Response(result, model, inputTokens, outputTokens);
        } catch (CustomException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new CustomException(ErrorCode.SHORTFORM_INVALID_AI_RESPONSE,
                    "OpenAI 제품 보강 응답 JSON을 해석할 수 없습니다.");
        }
    }

    private JsonNode execute(Map<String, Object> body) {
        RestClientException lastFailure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                JsonNode response = restClient.post()
                        .uri(properties.getApiUrl())
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
                log.info("OpenAI 제품 보강 HTTP 성공: status=200, model={}", properties.getProductModel());
                return response;
            } catch (RestClientResponseException exception) {
                lastFailure = exception;
                log.warn("OpenAI 제품 보강 HTTP 실패: status={}, model={}, attempt={}",
                        exception.getStatusCode().value(), properties.getProductModel(), attempt + 1);
                if (exception.getStatusCode().value() != 429
                        && !exception.getStatusCode().is5xxServerError()) {
                    break;
                }
            } catch (RestClientException exception) {
                lastFailure = exception;
                log.warn("OpenAI 제품 보강 연결 실패: model={}, attempt={}",
                        properties.getProductModel(), attempt + 1);
            }
            if (!waitBeforeRetry(attempt)) {
                break;
            }
        }
        throw new CustomException(
                ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE,
                lastFailure == null
                        ? "OpenAI 제품 보강 요청에 실패했습니다."
                        : "OpenAI 제품 보강 서비스를 일시적으로 사용할 수 없습니다."
        );
    }

    private boolean waitBeforeRetry(int attempt) {
        if (attempt >= 2) {
            return true;
        }
        try {
            Thread.sleep(250L << attempt);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
