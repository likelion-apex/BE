package domain.beauty.shortform.client;

import domain.beauty.shortform.config.ShortformAiFallbackProperties;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class GeminiStructuredOutputClient {

    private static final String API_PATH = "/v1beta/interactions";

    private final RestClient restClient;
    private final ShortformAiFallbackProperties fallbackProperties;
    private final ObjectMapper objectMapper;
    private final domain.beauty.config.GeminiProperties geminiProperties;
    private final GeminiModelRouter modelRouter;

    public GeminiStructuredOutputClient(
            RestClient geminiRestClient,
            domain.beauty.config.GeminiProperties geminiProperties,
            ShortformAiFallbackProperties fallbackProperties,
            ObjectMapper objectMapper,
            GeminiModelRouter modelRouter
    ) {
        this.restClient = geminiRestClient;
        this.geminiProperties = geminiProperties;
        this.fallbackProperties = fallbackProperties;
        this.objectMapper = objectMapper;
        this.modelRouter = modelRouter;
    }

    public Response generate(
            String operation,
            String systemPrompt,
            String userPrompt,
            JsonNode responseSchema,
            int maxOutputTokens
    ) {
        DecodedResponse<String> response = generateDecoded(
                operation, systemPrompt, userPrompt, responseSchema, maxOutputTokens, output -> output);
        return new Response(
                response.result(), response.model(), response.inputTokens(), response.outputTokens());
    }

    public <T> DecodedResponse<T> generateDecoded(
            String operation,
            String systemPrompt,
            String userPrompt,
            JsonNode responseSchema,
            int maxOutputTokens,
            OutputDecoder<T> decoder
    ) {
        if (geminiProperties.getApiKey() == null || geminiProperties.getApiKey().isBlank()) {
            throw new CustomException(
                    ErrorCode.SHORTFORM_CONFIGURATION_MISSING,
                    "GEMINI_API_KEY 환경변수가 필요합니다.");
        }

        try {
            DecodedResponse<T> response = modelRouter.route(
                    GeminiRouteProfile.TEXT,
                    operation,
                    model -> executeCandidate(
                            model,
                            systemPrompt,
                            userPrompt,
                            responseSchema,
                            Math.min(maxOutputTokens, fallbackProperties.getGeminiMaxOutputTokens()),
                            decoder));
            log.info("Gemini 숏폼 {} 완료: model={}, inputTokens={}, outputTokens={}",
                    operation, response.model(), response.inputTokens(), response.outputTokens());
            return response;
        } catch (GeminiModelRoutingException exception) {
            if (exception.isConfigurationFailure()) {
                throw new CustomException(
                        ErrorCode.SHORTFORM_CONFIGURATION_MISSING,
                        "Gemini API 키 또는 프로젝트 권한을 확인해 주세요.");
            }
            throw new CustomException(
                    ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE,
                    "Gemini 분석 서비스를 일시적으로 사용할 수 없습니다.");
        }
    }

    private <T> DecodedResponse<T> executeCandidate(
            String model,
            String systemPrompt,
            String userPrompt,
            JsonNode responseSchema,
            int maxOutputTokens,
            OutputDecoder<T> decoder
    ) {
        String responseBody = restClient.post()
                .uri(API_PATH)
                .header("x-goog-api-key", geminiProperties.getApiKey())
                .body(buildRequest(model, systemPrompt, userPrompt, responseSchema, maxOutputTokens))
                .retrieve()
                .body(String.class);
        try {
            Response parsed = parse(responseBody, model);
            T decoded = decoder.decode(parsed.outputText());
            return new DecodedResponse<>(
                    decoded, parsed.model(), parsed.inputTokens(), parsed.outputTokens());
        } catch (GeminiCandidateRejectedException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new GeminiCandidateRejectedException("응답 JSON을 해석할 수 없습니다.", exception);
        } catch (Exception exception) {
            throw new GeminiCandidateRejectedException("응답 검증에 실패했습니다.", exception);
        }
    }

    private Map<String, Object> buildRequest(
            String model,
            String systemPrompt,
            String userPrompt,
            JsonNode responseSchema,
            int maxOutputTokens
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
		request.put("model", model);
		request.put("system_instruction", systemPrompt
                + "\n\n반드시 아래 JSON 계약의 필드명과 구조를 따라 JSON 객체만 반환하세요.\n"
                + responseSchema.toString());
        request.put("input", userPrompt);
        request.put("response_format", Map.of(
                "type", "text",
                "mime_type", "application/json"));
        request.put("generation_config", Map.of(
                "max_output_tokens", Math.max(1, maxOutputTokens),
                "thinking_level", "low"));
        request.put("stream", false);
        request.put("store", false);
        return request;
    }

    private Response parse(String responseBody, String requestedModel) throws JacksonException {
        if (responseBody == null || responseBody.isBlank()) {
            throw new GeminiCandidateRejectedException("빈 응답을 반환했습니다.");
        }
        JsonNode envelope = objectMapper.readTree(responseBody);
        if (!"completed".equals(envelope.path("status").asString())) {
            throw new GeminiCandidateRejectedException("완료 상태가 아닙니다.");
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
            throw new GeminiCandidateRejectedException("분석 결과가 없습니다.");
        }
        JsonNode output = objectMapper.readTree(outputText);
        if (!output.isObject()) {
            throw new GeminiCandidateRejectedException("JSON 객체가 아닙니다.");
        }
        JsonNode usage = envelope.path("usage");
        return new Response(
                outputText,
                envelope.path("model").asString(requestedModel),
                usage.path("total_input_tokens").asLong(),
                usage.path("total_output_tokens").asLong());
    }

    public record Response(String outputText, String model, long inputTokens, long outputTokens) {
    }

    public record DecodedResponse<T>(T result, String model, long inputTokens, long outputTokens) {
    }

    @FunctionalInterface
    public interface OutputDecoder<T> {
        T decode(String outputText) throws Exception;
    }
}
