package domain.beauty.shortform.client;

import domain.beauty.shortform.client.OptimizationReasonResult.Response;
import domain.beauty.shortform.config.OpenAiRoutineProperties;
import domain.beauty.shortform.config.ShortformAiFallbackProperties;
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
public class OpenAiOptimizationReasonClient {

    private final RestClient restClient;
    private final OpenAiRoutineProperties properties;
    private final OpenAiOptimizationReasonPromptResources promptResources;
    private final GeminiStructuredOutputClient geminiClient;
    private final ShortformAiFallbackProperties fallbackProperties;
    private final ObjectMapper objectMapper;

    public OpenAiOptimizationReasonClient(
            @Qualifier("shortformOpenAiRestClient") RestClient restClient,
            OpenAiRoutineProperties properties,
            OpenAiOptimizationReasonPromptResources promptResources,
            GeminiStructuredOutputClient geminiClient,
            ShortformAiFallbackProperties fallbackProperties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.promptResources = promptResources;
        this.geminiClient = geminiClient;
        this.fallbackProperties = fallbackProperties;
        this.objectMapper = objectMapper;
    }

    public Response generate(OptimizationReasonInput input) {
        try {
            return generateWithOpenAi(input);
        } catch (CustomException openAiFailure) {
            if (!fallbackProperties.isGeminiEnabled()) {
                throw openAiFailure;
            }
            log.warn("OpenAI 최적화 이유 생성 실패 후 Gemini로 전환합니다: reason={}",
                    openAiFailure.getErrorCode());
            try {
                return generateWithGemini(input);
            } catch (CustomException geminiFailure) {
                log.warn("Gemini 최적화 이유 생성 폴백 실패: reason={}", geminiFailure.getErrorCode());
                throw new CustomException(
                        ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE,
                        "OpenAI와 Gemini 분석 서비스를 일시적으로 사용할 수 없습니다.");
            }
        }
    }

    private Response generateWithOpenAi(OptimizationReasonInput input) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new CustomException(ErrorCode.SHORTFORM_CONFIGURATION_MISSING, "OPENAI_API_KEY 환경변수가 필요합니다.");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", properties.getRoutineModel());
            body.put("temperature", 0);
            body.put("max_tokens", Math.min(properties.getMaxOutputTokens(), 2000));
            body.put("response_format", Map.of(
                    "type", "json_schema",
                    "json_schema", Map.of(
                            "name", "shortform_optimization_reasons",
                            "strict", true,
                            "schema", promptResources.responseSchema()
                    )
            ));
            body.put("messages", List.of(
                    Map.of("role", "system", "content", promptResources.systemPrompt()),
                    Map.of("role", "user", "content", "다음 JSON 데이터만 근거로 이유 문구를 작성하세요.\n"
                            + objectMapper.writeValueAsString(input))
            ));

            JsonNode envelope = execute(body);
            String content = envelope.path("choices").path(0).path("message").path("content").stringValue(null);
            if (content == null || content.isBlank()) {
                throw new CustomException(ErrorCode.SHORTFORM_INVALID_AI_RESPONSE);
            }
            OptimizationReasonResult result = objectMapper.readValue(content, OptimizationReasonResult.class);
            JsonNode usage = envelope.path("usage");
            return new Response(
                    result,
                    envelope.path("model").stringValue(properties.getRoutineModel()),
                    usage.path("prompt_tokens").asLong(),
                    usage.path("completion_tokens").asLong());
        } catch (CustomException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new CustomException(ErrorCode.SHORTFORM_INVALID_AI_RESPONSE, "OpenAI 이유 응답 JSON을 해석할 수 없습니다.");
        }
    }

    private Response generateWithGemini(OptimizationReasonInput input) {
        try {
            GeminiStructuredOutputClient.DecodedResponse<OptimizationReasonResult> response = geminiClient.generateDecoded(
                    "최적화 이유 생성",
                    promptResources.systemPrompt(),
                    "다음 JSON 데이터만 근거로 이유 문구를 작성하세요.\n"
                            + objectMapper.writeValueAsString(input),
                    promptResources.responseSchema(),
                    Math.min(properties.getMaxOutputTokens(), 2_000),
                    output -> GeminiStructuredResultValidator.validateOptimization(
                            input,
                            objectMapper.readValue(output, OptimizationReasonResult.class)));
            return new Response(
                    response.result(),
                    response.model(),
                    response.inputTokens(),
                    response.outputTokens());
        } catch (CustomException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new CustomException(
                    ErrorCode.SHORTFORM_INVALID_AI_RESPONSE,
                    "Gemini 이유 응답 JSON을 해석할 수 없습니다.");
        }
    }

    private JsonNode execute(Map<String, Object> body) {
        RestClientException lastFailure = null;
        int maxAttempts = fallbackProperties.isGeminiEnabled() ? 1 : 3;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return restClient.post()
                        .uri(properties.getApiUrl())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                        .headers(headers -> {
                            if (properties.getOrganizationId() != null && !properties.getOrganizationId().isBlank()) {
                                headers.set("OpenAI-Organization", properties.getOrganizationId());
                            }
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
            } catch (RestClientResponseException exception) {
                lastFailure = exception;
                if (exception.getStatusCode().value() != 429 && !exception.getStatusCode().is5xxServerError()) {
                    break;
                }
            } catch (RestClientException exception) {
                lastFailure = exception;
            }
            if (!waitBeforeRetry(attempt, maxAttempts)) {
                break;
            }
        }
        log.warn("OpenAI 기존 최적화 이유 갱신 실패: model={}", properties.getRoutineModel());
        throw new CustomException(
                ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE,
                lastFailure == null ? "OpenAI 이유 생성 요청에 실패했습니다." : "OpenAI 분석 서비스를 일시적으로 사용할 수 없습니다.");
    }

    private boolean waitBeforeRetry(int attempt, int maxAttempts) {
        if (attempt >= maxAttempts - 1) {
            return false;
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
