package domain.inventory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import domain.beauty.config.GeminiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class InventoryGeminiJsonClientTest {

    @Test
    void parsesGenerateContentJsonText() {
        ObjectMapper objectMapper = new ObjectMapper();
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey("test-gemini-key");
        properties.setModel("gemini-3.6-flash");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InventoryGeminiJsonClient client = new InventoryGeminiJsonClient(
                builder.build(), properties, new InventoryAiProperties(), objectMapper);
        server.expect(requestTo("https://gemini.test/v1beta/models/gemini-3.6-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-gemini-key"))
                .andRespond(withSuccess("""
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  { "text": "{\\"ingredients\\":[\\"정제수\\"]}" }
                                ]
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        JsonNode payload = client.generateJson("system", "user");

        assertThat(payload.path("ingredients").get(0).asText()).isEqualTo("정제수");
        server.verify();
    }
}
