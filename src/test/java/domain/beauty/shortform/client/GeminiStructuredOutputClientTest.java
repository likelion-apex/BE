package domain.beauty.shortform.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import domain.beauty.config.GeminiProperties;
import domain.beauty.shortform.config.ShortformAiFallbackProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GeminiStructuredOutputClientTest {

    @Test
    void sendsSharedPromptAndSchemaToGeminiInteractions() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        GeminiProperties geminiProperties = new GeminiProperties();
        geminiProperties.setApiKey("test-gemini-key");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String output = "{\"result\":\"ok\"}";
        String response = """
                {
                  "status":"completed",
                  "model":"gemini-test",
                  "usage":{"total_input_tokens":12,"total_output_tokens":8},
                  "steps":[{"type":"model_output","content":[{"type":"text","text":%s}]}]
                }
                """.formatted(objectMapper.writeValueAsString(output));
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andExpect(header("x-goog-api-key", "test-gemini-key"))
                .andExpect(content().string(containsString("system_instruction")))
                .andExpect(content().string(containsString("application/json")))
                .andExpect(request -> {
                    var body = objectMapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertThat(body.path("response_format").has("schema")).isFalse();
                    assertThat(body.path("system_instruction").asString())
                            .contains("JSON 계약", "\"type\":\"object\"");
                })
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        GeminiStructuredOutputClient client = new GeminiStructuredOutputClient(
                builder.build(),
                geminiProperties,
                new ShortformAiFallbackProperties(),
                objectMapper,
                new GeminiModelRouter(geminiProperties, new ShortformAiFallbackProperties()));

        GeminiStructuredOutputClient.Response result = client.generate(
                "테스트",
                "시스템 프롬프트",
                "사용자 입력",
                objectMapper.readTree("{\"type\":\"object\"}"),
                100);

        assertThat(result.outputText()).isEqualTo(output);
        assertThat(result.model()).isEqualTo("gemini-test");
        assertThat(result.inputTokens()).isEqualTo(12);
        assertThat(result.outputTokens()).isEqualTo(8);
        server.verify();
    }

    @Test
    void routesToNextTextModelWhenDecodedCandidateIsInvalid() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        GeminiProperties geminiProperties = new GeminiProperties();
        geminiProperties.setApiKey("test-gemini-key");
        ShortformAiFallbackProperties routing = new ShortformAiFallbackProperties();
        routing.setGeminiTextModels(List.of("model-a", "model-b"));
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andExpect(content().string(containsString("\"model\":\"model-a\"")))
                .andRespond(withSuccess(interaction(objectMapper, "model-a", "{\"result\":\"invented\"}"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andExpect(content().string(containsString("\"model\":\"model-b\"")))
                .andRespond(withSuccess(interaction(objectMapper, "model-b", "{\"result\":\"ok\"}"),
                        MediaType.APPLICATION_JSON));

        GeminiStructuredOutputClient client = new GeminiStructuredOutputClient(
                builder.build(),
                geminiProperties,
                routing,
                objectMapper,
                new GeminiModelRouter(geminiProperties, routing));

        GeminiStructuredOutputClient.DecodedResponse<String> result = client.generateDecoded(
                "테스트",
                "시스템 프롬프트",
                "사용자 입력",
                objectMapper.readTree("{\"type\":\"object\"}"),
                100,
                output -> {
                    String value = objectMapper.readTree(output).path("result").asString();
                    if (!"ok".equals(value)) {
                        throw new GeminiCandidateRejectedException("입력 근거와 일치하지 않습니다.");
                    }
                    return value;
                });

        assertThat(result.result()).isEqualTo("ok");
        assertThat(result.model()).isEqualTo("model-b");
        server.verify();
    }

    private String interaction(ObjectMapper objectMapper, String model, String output) throws Exception {
        return """
                {
                  "status":"completed",
                  "model":"%s",
                  "usage":{"total_input_tokens":12,"total_output_tokens":8},
                  "steps":[{"type":"model_output","content":[{"type":"text","text":%s}]}]
                }
                """.formatted(model, objectMapper.writeValueAsString(output));
    }
}
