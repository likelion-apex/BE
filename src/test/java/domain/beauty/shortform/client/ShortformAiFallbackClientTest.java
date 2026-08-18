package domain.beauty.shortform.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import domain.beauty.shortform.config.OpenAiRoutineProperties;
import domain.beauty.shortform.config.ShortformAiFallbackProperties;
import domain.beauty.shortform.domain.IngredientDataStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ShortformAiFallbackClientTest {

    @Test
    void fallsBackToGeminiWhenRoutineOpenAiIsRateLimited() {
        ObjectMapper objectMapper = new ObjectMapper();
        OpenAiRoutineProperties openAiProperties = new OpenAiRoutineProperties();
        openAiProperties.setApiKey("test-openai-key");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(openAiProperties.getApiUrl().toString()))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        GeminiStructuredOutputClient geminiClient = mock(GeminiStructuredOutputClient.class);
        when(geminiClient.generate(anyString(), anyString(), anyString(), any(JsonNode.class), anyInt()))
                .thenReturn(new GeminiStructuredOutputClient.Response(
                        routineResultJson(), "gemini-test", 120, 80));
        OpenAiRoutineAnalysisClient client = new OpenAiRoutineAnalysisClient(
                builder.build(),
                openAiProperties,
                new OpenAiRoutinePromptResources(objectMapper, openAiProperties),
                geminiClient,
                new ShortformAiFallbackProperties(),
                objectMapper);

        RoutinePersonalizationResult.Response response = client.analyze(routineInput());

        assertThat(response.model()).isEqualTo("gemini-test");
        assertThat(response.analysis().title()).isEqualTo("민감 피부 진정 루틴");
        verify(geminiClient).generate(anyString(), anyString(), anyString(), any(JsonNode.class), anyInt());
        server.verify();
    }

    @Test
    void fallsBackToGeminiWhenOptimizationOpenAiConfigurationIsMissing() {
        ObjectMapper objectMapper = new ObjectMapper();
        OpenAiRoutineProperties openAiProperties = new OpenAiRoutineProperties();
        GeminiStructuredOutputClient geminiClient = mock(GeminiStructuredOutputClient.class);
        when(geminiClient.generate(anyString(), anyString(), anyString(), any(JsonNode.class), anyInt()))
                .thenReturn(new GeminiStructuredOutputClient.Response(
                        "{\"steps\":[{\"order\":1,\"reason\":\"진정 성분을 확인한 보유 제품으로 대체할 수 있어요.\"}]}",
                        "gemini-test", 40, 20));
        OpenAiOptimizationReasonClient client = new OpenAiOptimizationReasonClient(
                RestClient.create(),
                openAiProperties,
                new OpenAiOptimizationReasonPromptResources(objectMapper),
                geminiClient,
                new ShortformAiFallbackProperties(),
                objectMapper);
        OptimizationReasonInput input = new OptimizationReasonInput(
                new OptimizationReasonInput.MemberProfile("민감성", List.of("피부 진정")),
                List.of());

        OptimizationReasonResult.Response response = client.generate(input);

        assertThat(response.model()).isEqualTo("gemini-test");
        assertThat(response.result().steps()).singleElement()
                .satisfies(step -> assertThat(step.reason()).contains("보유 제품"));
        verify(geminiClient).generate(anyString(), anyString(), anyString(), any(JsonNode.class), anyInt());
    }

    @Test
    void disablesGeminiFallbackWithEnvironmentSwitch() {
        ObjectMapper objectMapper = new ObjectMapper();
        OpenAiRoutineProperties openAiProperties = new OpenAiRoutineProperties();
        ShortformAiFallbackProperties fallbackProperties = new ShortformAiFallbackProperties();
        fallbackProperties.setGeminiEnabled(false);
        GeminiStructuredOutputClient geminiClient = mock(GeminiStructuredOutputClient.class);
        OpenAiRoutineAnalysisClient client = new OpenAiRoutineAnalysisClient(
                RestClient.create(),
                openAiProperties,
                new OpenAiRoutinePromptResources(objectMapper, openAiProperties),
                geminiClient,
                fallbackProperties,
                objectMapper);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.analyze(routineInput()))
                .isInstanceOf(global.exception.CustomException.class)
                .hasMessageContaining("OPENAI_API_KEY");
        verify(geminiClient, times(0))
                .generate(anyString(), anyString(), anyString(), any(JsonNode.class), anyInt());
    }

    private RoutinePersonalizationInput routineInput() {
        return new RoutinePersonalizationInput(
                new RoutinePersonalizationInput.MemberProfile("민감성", List.of("피부 진정")),
                new RoutinePersonalizationInput.VideoContext("video", "진정 루틴", List.of()),
                List.of(new RoutinePersonalizationInput.VideoStep(
                        1, "앰플", "SERUM", "브랜드", "진정 앰플", "브랜드", "진정 앰플",
                        "피부 진정", "영상에서 확인", 1.0, null,
                        IngredientDataStatus.UNAVAILABLE, List.of())),
                List.of());
    }

    private String routineResultJson() {
        return """
                {
                  "title":"민감 피부 진정 루틴",
                  "tag":"진정",
                  "highlights":["피부 진정"],
                  "coreGoal":"민감 피부 진정",
                  "synergyCombo":"진정 단계",
                  "summary":"진정 앰플로 피부를 편안하게 관리하는 루틴입니다.",
                  "warnings":[],
                  "steps":[{
                    "order":1,
                    "scoreBreakdown":{"skinTypeFit":30,"benefitFit":25},
                    "keyBenefits":["피부 진정"],
                    "reasons":[
                      {"assessmentCategory":"SAFE","title":"순한 사용","description":"확인된 단계 목적에 맞아요.","evidenceSource":"영상"},
                      {"assessmentCategory":"BENEFICIAL","title":"피부 진정","description":"피부 진정 단계로 사용할 수 있어요.","evidenceSource":"영상"}
                    ]
                  }],
                  "inventoryRecommendations":[{"order":1,"inventoryId":null,"reason":"확인된 대체 제품이 없어요."}]
                }
                """;
    }
}
