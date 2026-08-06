package feat.apex_BE.beauty.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import feat.apex_BE.beauty.config.GeminiProperties;
import feat.apex_BE.beauty.domain.BeautyRoutineAnalysis;
import feat.apex_BE.beauty.domain.BeautyRoutineAnalysisResult;
import feat.apex_BE.beauty.domain.BeautyRoutineAnalysisResult.TokenUsage;
import feat.apex_BE.beauty.domain.NormalizedYouTubeVideo;
import feat.apex_BE.beauty.exception.BeautyRoutineException.GeminiUnavailable;
import feat.apex_BE.beauty.exception.BeautyRoutineException.InvalidGeminiResponse;
import feat.apex_BE.beauty.exception.BeautyRoutineException.MissingGeminiConfiguration;
import feat.apex_BE.beauty.support.BeautyRoutineAnalysisValidator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GeminiBeautyRoutineClient implements BeautyRoutineGateway {

	private static final String API_PATH = "/v1beta/interactions";

	private final RestClient restClient;
	private final GeminiProperties properties;
	private final GeminiPromptResources promptResources;
	private final ObjectMapper objectMapper;
	private final BeautyRoutineAnalysisValidator validator;

	public GeminiBeautyRoutineClient(
		RestClient geminiRestClient,
		GeminiProperties properties,
		GeminiPromptResources promptResources,
		ObjectMapper objectMapper,
		BeautyRoutineAnalysisValidator validator
	) {
		this.restClient = geminiRestClient;
		this.properties = properties;
		this.promptResources = promptResources;
		this.objectMapper = objectMapper;
		this.validator = validator;
	}

	@Override
	public BeautyRoutineAnalysisResult analyze(NormalizedYouTubeVideo video) {
		if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
			throw new MissingGeminiConfiguration("GEMINI_API_KEY 환경변수가 설정되지 않았습니다.");
		}

		try {
			String responseBody = restClient.post()
				.uri(API_PATH)
				.header("x-goog-api-key", properties.getApiKey())
				.body(buildRequest(video.watchUrl()))
				.retrieve()
				.body(String.class);

			return parseResponse(video, responseBody);
		} catch (RestClientResponseException exception) {
			if (exception.getStatusCode().is5xxServerError()
				|| exception.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
				throw new GeminiUnavailable("Gemini가 일시적으로 요청을 처리할 수 없습니다.", exception);
			}
			if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED
				|| exception.getStatusCode() == HttpStatus.FORBIDDEN) {
				throw new MissingGeminiConfiguration("Gemini API 키 또는 프로젝트 권한을 확인해 주세요.");
			}
			throw new InvalidGeminiResponse("Gemini 요청이 거부되었습니다.", exception);
		} catch (ResourceAccessException exception) {
			throw new GeminiUnavailable("Gemini 응답 시간이 초과되었거나 연결할 수 없습니다.", exception);
		}
	}

	private Map<String, Object> buildRequest(String watchUrl) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("model", properties.getModel());
		request.put("system_instruction", promptResources.systemPrompt());
		request.put("input", List.of(
			Map.of("type", "video", "uri", watchUrl),
			Map.of("type", "text", "text", promptResources.userPrompt())
		));
		request.put("response_format", Map.of(
			"type", "text",
			"mime_type", "application/json",
			"schema", promptResources.responseSchema()
		));
		request.put("generation_config", Map.of(
			"max_output_tokens", 4_000,
			"seed", 42,
			"thinking_level", "low"
		));
		request.put("stream", false);
		request.put("store", false);
		return request;
	}

	private BeautyRoutineAnalysisResult parseResponse(NormalizedYouTubeVideo video, String responseBody) {
		if (responseBody == null || responseBody.isBlank()) {
			throw new InvalidGeminiResponse("Gemini가 빈 응답을 반환했습니다.");
		}

		try {
			JsonNode envelope = objectMapper.readTree(responseBody);
			if (!"completed".equals(envelope.path("status").asString())) {
				throw new InvalidGeminiResponse("Gemini 분석이 완료 상태가 아닙니다.");
			}

			String outputText = extractLastModelOutput(envelope.path("steps"));
			BeautyRoutineAnalysis parsedAnalysis = objectMapper.readValue(outputText, BeautyRoutineAnalysis.class);
			BeautyRoutineAnalysis analysis = validator.validateAndNormalize(parsedAnalysis);
			TokenUsage usage = parseUsage(envelope.path("usage"));
			String model = envelope.path("model").asString(properties.getModel());

			return new BeautyRoutineAnalysisResult(
				video.videoId(),
				video.watchUrl(),
				model,
				usage,
				analysis
			);
		} catch (JacksonException exception) {
			throw new InvalidGeminiResponse("Gemini JSON 응답을 해석할 수 없습니다.", exception);
		}
	}

	private String extractLastModelOutput(JsonNode steps) {
		if (!steps.isArray()) {
			throw new InvalidGeminiResponse("Gemini 응답에 분석 단계가 없습니다.");
		}

		for (int index = steps.size() - 1; index >= 0; index--) {
			JsonNode step = steps.get(index);
			if (!"model_output".equals(step.path("type").asString())) {
				continue;
			}

			List<String> texts = new ArrayList<>();
			for (JsonNode content : step.path("content")) {
				if ("text".equals(content.path("type").asString()) && content.path("text").isString()) {
					texts.add(content.path("text").asString());
				}
			}
			if (!texts.isEmpty()) {
				return String.join("", texts);
			}
		}

		throw new InvalidGeminiResponse("Gemini 응답에 텍스트 분석 결과가 없습니다.");
	}

	private TokenUsage parseUsage(JsonNode usageNode) {
		if (!usageNode.isObject()) {
			return new TokenUsage(0, 0, 0, 0);
		}
		return new TokenUsage(
			usageNode.path("total_input_tokens").asLong(),
			usageNode.path("total_output_tokens").asLong(),
			usageNode.path("total_thought_tokens").asLong(),
			usageNode.path("total_tokens").asLong()
		);
	}
}
