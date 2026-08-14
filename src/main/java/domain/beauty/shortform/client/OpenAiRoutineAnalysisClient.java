package domain.beauty.shortform.client;

import domain.beauty.shortform.config.OpenAiRoutineProperties;
import domain.beauty.shortform.client.RoutinePersonalizationResult.Response;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

@Component
public class OpenAiRoutineAnalysisClient {

    private final RestClient restClient;
    private final OpenAiRoutineProperties properties;
    private final OpenAiRoutinePromptResources promptResources;
    private final ObjectMapper objectMapper;

    public OpenAiRoutineAnalysisClient(
            @Qualifier("shortformOpenAiRestClient") RestClient restClient,
            OpenAiRoutineProperties properties,
            OpenAiRoutinePromptResources promptResources,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.promptResources = promptResources;
        this.objectMapper = objectMapper;
    }

    public Response analyze(RoutinePersonalizationInput input) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new CustomException(ErrorCode.SHORTFORM_CONFIGURATION_MISSING, "OPENAI_API_KEY 환경변수가 필요합니다.");
        }

        try {
            String inputJson = objectMapper.writeValueAsString(input);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", properties.getRoutineModel());
            body.put("temperature", 0);
            body.put("max_tokens", properties.getMaxOutputTokens());
            body.put("response_format", Map.of(
                    "type", "json_schema",
                    "json_schema", Map.of(
                            "name", "shortform_routine_analysis",
                            "strict", true,
                            "schema", promptResources.responseSchema()
                    )
            ));
            body.put("messages", List.of(
                    Map.of("role", "system", "content", promptResources.systemPrompt()),
                    Map.of("role", "user", "content", "다음 JSON 데이터만 근거로 분석하세요.\n" + inputJson)
            ));

            JsonNode envelope = execute(body);
            String content = envelope.path("choices").path(0).path("message").path("content").stringValue(null);
            if (content == null || content.isBlank()) {
                throw new CustomException(ErrorCode.SHORTFORM_INVALID_AI_RESPONSE);
            }

            RoutinePersonalizationResult analysis = objectMapper.readValue(content, RoutinePersonalizationResult.class);
            JsonNode usage = envelope.path("usage");
            String model = envelope.path("model").stringValue(properties.getRoutineModel());
            return new Response(
                    analysis,
                    model,
                    usage.path("prompt_tokens").asLong(),
                    usage.path("completion_tokens").asLong()
            );
        } catch (CustomException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new CustomException(ErrorCode.SHORTFORM_INVALID_AI_RESPONSE, "OpenAI 응답 JSON을 해석할 수 없습니다.");
        }
    }

    private JsonNode execute(Map<String, Object> body) {
        RestClientException lastFailure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
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
            if (!waitBeforeRetry(attempt)) {
                break;
            }
        }
        throw new CustomException(
                ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE,
                lastFailure == null ? "OpenAI 분석 요청에 실패했습니다." : "OpenAI 분석 서비스를 일시적으로 사용할 수 없습니다."
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
