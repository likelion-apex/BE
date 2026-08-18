package domain.beauty.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import domain.beauty.config.GeminiProperties;
import domain.beauty.domain.BeautyRoutineAnalysis.IdentificationLevel;
import domain.beauty.domain.BeautyRoutineAnalysisResult;
import domain.beauty.domain.NormalizedYouTubeVideo;
import domain.beauty.shortform.client.GeminiModelRouter;
import domain.beauty.shortform.config.ShortformAiFallbackProperties;
import domain.beauty.support.BeautyRoutineAnalysisValidator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GeminiBeautyRoutineClientTest {

	private ObjectMapper objectMapper;
	private GeminiProperties properties;
	private MockRestServiceServer server;
	private GeminiBeautyRoutineClient client;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		properties = new GeminiProperties();
		properties.setApiKey("test-api-key");

		RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl().toString());
		server = MockRestServiceServer.bindTo(builder).build();
		client = new GeminiBeautyRoutineClient(
			builder.build(),
			properties,
			new GeminiPromptResources(objectMapper),
			objectMapper,
			new BeautyRoutineAnalysisValidator(),
			new GeminiModelRouter(properties, new ShortformAiFallbackProperties())
		);
	}

	@Test
	void sendsVideoBeforePromptAndParsesStructuredOutput() throws Exception {
		String analysisJson = """
			{
			  "schemaVersion":"1.0",
			  "analysisStatus":"COMPLETE",
			  "routineType":"SKINCARE",
			  "summary":"토너를 바르는 스킨케어 루틴입니다.",
			  "steps":[{
			    "order":1,
			    "startTime":"00:01",
			    "endTime":"00:03",
			    "applicationArea":"얼굴",
			    "action":"토너를 바릅니다.",
			    "technique":"화장솜으로 닦습니다.",
			    "purpose":"피부 결 정돈",
			    "purposeBasis":"GENERAL_INFERENCE",
			    "applicator":"화장솜",
			    "identificationLevel":"EXACT_PRODUCT",
			    "category":"토너",
			    "brand":"라운드랩",
			    "productName":"1025 독도 토너",
			    "variant":null,
			    "identityEvidenceText":"라운드랩 1025 독도 토너",
			    "observedColor":null,
			    "evidenceSources":["ON_SCREEN_TEXT","VISUAL_USAGE"],
			    "evidenceSummary":"화면 문구와 사용 장면에서 확인했습니다.",
			    "confidence":0.98
			  }],
			  "warnings":[]
			}
			""";
		String envelope = objectMapper.writeValueAsString(Map.of(
			"model", "gemini-3.5-flash",
			"status", "completed",
			"steps", List.of(Map.of(
				"type", "model_output",
				"content", List.of(Map.of("type", "text", "text", analysisJson))
			)),
			"usage", Map.of(
				"total_input_tokens", 5000,
				"total_output_tokens", 1000,
				"total_thought_tokens", 0,
				"total_tokens", 6000
			)
		));

		server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("x-goog-api-key", "test-api-key"))
			.andExpect(request -> {
				String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
				assertThat(body).contains("\"response_format\"");
				assertThat(body.indexOf("\"type\":\"video\""))
					.isLessThan(body.indexOf("\"type\":\"text\""));
			})
			.andRespond(withSuccess(envelope, MediaType.APPLICATION_JSON));

		BeautyRoutineAnalysisResult result = client.analyze(
			new NormalizedYouTubeVideo("-PC1SkLxtvo", "https://www.youtube.com/watch?v=-PC1SkLxtvo")
		);

		assertThat(result.model()).isEqualTo("gemini-3.5-flash");
		assertThat(result.usage().totalTokens()).isEqualTo(6000);
		assertThat(result.analysis().steps()).hasSize(1);
		assertThat(result.analysis().steps().getFirst().identificationLevel())
			.isEqualTo(IdentificationLevel.EXACT_PRODUCT);
		server.verify();
	}

	@Test
	void routesToNextVideoModelWhenPrimaryIsRateLimited() throws Exception {
		server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
			.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":{\"message\":\"quota exceeded\"}}"));
		String emptyRoutine = """
			{"schemaVersion":"1.0","analysisStatus":"COMPLETE","routineType":"SKINCARE","summary":"토너 루틴","steps":[{"order":1,"startTime":"00:01","endTime":"00:03","applicationArea":"얼굴","action":"토너를 바릅니다.","technique":"손으로 바릅니다.","purpose":"정돈","purposeBasis":"GENERAL_INFERENCE","applicator":null,"identificationLevel":"CATEGORY_ONLY","category":"토너","brand":null,"productName":null,"variant":null,"identityEvidenceText":null,"observedColor":null,"evidenceSources":["VISUAL_USAGE"],"evidenceSummary":"사용 장면","confidence":0.8}],"warnings":[]}
			""";
		String envelope = objectMapper.writeValueAsString(Map.of(
			"model", "gemini-3.5-flash",
			"status", "completed",
			"steps", List.of(Map.of("type", "model_output", "content", List.of(
				Map.of("type", "text", "text", emptyRoutine))))));
		server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
			.andExpect(request -> assertThat(
				((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString())
				.contains("gemini-3.5-flash"))
			.andRespond(withSuccess(envelope, MediaType.APPLICATION_JSON));

		BeautyRoutineAnalysisResult result = client.analyze(
			new NormalizedYouTubeVideo("-PC1SkLxtvo", "https://www.youtube.com/watch?v=-PC1SkLxtvo")
		);

		assertThat(result.model()).isEqualTo("gemini-3.5-flash");
		server.verify();
	}
}
