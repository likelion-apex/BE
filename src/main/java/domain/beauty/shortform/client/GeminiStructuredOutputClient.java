package domain.beauty.shortform.client;

import domain.beauty.config.GeminiProperties;
import domain.beauty.shortform.config.ShortformAiFallbackProperties;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class GeminiStructuredOutputClient {

    private static final String API_PATH = "/v1beta/interactions";

    private final RestClient restClient;
    private final GeminiProperties geminiProperties;
    private final ShortformAiFallbackProperties fallbackProperties;
    private final ObjectMapper objectMapper;

    public GeminiStructuredOutputClient(
            RestClient geminiRestClient,
            GeminiProperties geminiProperties,
            ShortformAiFallbackProperties fallbackProperties,
            ObjectMapper objectMapper
    ) {
        this.restClient = geminiRestClient;
        this.geminiProperties = geminiProperties;
        this.fallbackProperties = fallbackProperties;
        this.objectMapper = objectMapper;
    }

    public Response generate(
            String operation,
            String systemPrompt,
            String userPrompt,
            JsonNode responseSchema,
            int maxOutputTokens
    ) {
        if (geminiProperties.getApiKey() == null || geminiProperties.getApiKey().isBlank()) {
            throw new CustomException(
                    ErrorCode.SHORTFORM_CONFIGURATION_MISSING,
                    "GEMINI_API_KEY 환경변수가 필요합니다.");
        }

        try {
            String responseBody = executeWithRetry(buildRequest(
                    systemPrompt,
                    userPrompt,
                    responseSchema,
                    Math.min(maxOutputTokens, fallbackProperties.getGeminiMaxOutputTokens())), operation);
            Response response = parse(responseBody);
            log.info("Gemini 숏폼 {} 완료: model={}, inputTokens={}, outputTokens={}",
                    operation, response.model(), response.inputTokens(), response.outputTokens());
            return response;
        } catch (CustomException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            log.warn("Gemini 숏폼 {} HTTP 실패: status={}, model={}",
                    operation, exception.getStatusCode().value(), geminiProperties.getModel());
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED
                    || exception.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new CustomException(
                        ErrorCode.SHORTFORM_CONFIGURATION_MISSING,
                        "Gemini API 키 또는 프로젝트 권한을 확인해 주세요.");
            }
            throw new CustomException(
                    ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE,
                    "Gemini 분석 서비스를 일시적으로 사용할 수 없습니다.");
        } catch (ResourceAccessException exception) {
            throw new CustomException(
                    ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE,
                    "Gemini 분석 응답 시간이 초과되었습니다.");
        } catch (RestClientException exception) {
            throw new CustomException(
                    ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE,
                    "Gemini 분석 서비스에 연결할 수 없습니다.");
        } catch (JacksonException exception) {
            throw new CustomException(
                    ErrorCode.SHORTFORM_INVALID_AI_RESPONSE,
                    "Gemini 분석 응답 JSON을 해석할 수 없습니다.");
        }
    }

    private Map<String, Object> buildRequest(
            String systemPrompt,
            String userPrompt,
            JsonNode responseSchema,
            int maxOutputTokens
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", geminiProperties.getModel());
        request.put("system_instruction", systemPrompt);
        request.put("input", userPrompt);
        request.put("response_format", Map.of(
                "type", "text",
                "mime_type", "application/json",
                "schema", responseSchema));
        request.put("generation_config", Map.of(
                "max_output_tokens", Math.max(1, maxOutputTokens),
                "thinking_level", "low"));
        request.put("stream", false);
        request.put("store", false);
        return request;
    }

    private String executeWithRetry(Map<String, Object> request, String operation) {
        RestClientResponseException lastFailure = null;
        int maxAttempts = Math.max(1, fallbackProperties.getGeminiMaxAttempts());
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return restClient.post()
                        .uri(API_PATH)
                        .header("x-goog-api-key", geminiProperties.getApiKey())
                        .body(request)
                        .retrieve()
                        .body(String.class);
            } catch (RestClientResponseException exception) {
                lastFailure = exception;
                log.warn("Gemini 숏폼 {} HTTP 실패: status={}, model={}, attempt={}",
                        operation,
                        exception.getStatusCode().value(),
                        geminiProperties.getModel(),
                        attempt + 1);
                if (!retryable(exception) || quotaExhausted(exception) || !waitBeforeRetry(attempt, maxAttempts)) {
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
        return body != null && (body.contains("exceeded your current quota") || body.contains("RESOURCE_EXHAUSTED"));
    }

    private boolean waitBeforeRetry(int attempt, int maxAttempts) {
        if (attempt >= maxAttempts - 1) {
            return false;
        }
        try {
            Thread.sleep(250L << Math.min(attempt, 4));
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Response parse(String responseBody) throws JacksonException {
        if (responseBody == null || responseBody.isBlank()) {
            throw new CustomException(
                    ErrorCode.SHORTFORM_INVALID_AI_RESPONSE,
                    "Gemini가 빈 응답을 반환했습니다.");
        }
        JsonNode envelope = objectMapper.readTree(responseBody);
        if (!"completed".equals(envelope.path("status").asString())) {
            throw new CustomException(
                    ErrorCode.SHORTFORM_INVALID_AI_RESPONSE,
                    "Gemini 분석이 완료 상태가 아닙니다.");
        }

        String outputText = null;
        for (JsonNode step : envelope.path("steps")) {
            if (!"model_output".equals(step.path("type").asString())) {
                continue;
            }
            for (JsonNode content : step.path("content")) {
                if ("text".equals(content.path("type").asString()) && content.path("text").isString()) {
                    outputText = content.path("text").asString();
                }
            }
        }
        if (outputText == null || outputText.isBlank()) {
            throw new CustomException(
                    ErrorCode.SHORTFORM_INVALID_AI_RESPONSE,
                    "Gemini 응답에 분석 결과가 없습니다.");
        }
        JsonNode usage = envelope.path("usage");
        return new Response(
                outputText,
                envelope.path("model").asString(geminiProperties.getModel()),
                usage.path("total_input_tokens").asLong(),
                usage.path("total_output_tokens").asLong());
    }

    public record Response(String outputText, String model, long inputTokens, long outputTokens) {
    }
}
