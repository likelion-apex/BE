package domain.beauty.shortform.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import domain.beauty.shortform.config.OpenAiRoutineProperties;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class OpenAiProductEnrichmentClientTest {

    @Test
    void sendsOneStructuredBatchAndParsesFullIngredientFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        OpenAiRoutineProperties properties = new OpenAiRoutineProperties();
        properties.setApiKey("test-openai-key");
        properties.setApiUrl(URI.create("https://api.openai.test/v1/chat/completions"));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiProductEnrichmentClient client = new OpenAiProductEnrichmentClient(
                builder.build(),
                properties,
                new OpenAiProductEnrichmentPromptResources(objectMapper),
                objectMapper
        );
        server.expect(requestTo("https://api.openai.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-openai-key"))
                .andExpect(content().string(containsString("shortform_product_enrichment")))
                .andExpect(content().string(containsString("repairMissingIngredients")))
                .andRespond(withSuccess(response(), MediaType.APPLICATION_JSON));

        ProductEnrichmentResult.Response response = client.enrich(new ProductEnrichmentInput(
                false,
                List.of(new ProductEnrichmentInput.Product(
                        "request-1", "토너", "ROUND LAB", "1025 Dokdo Toner", null,
                        "ROUND LAB 1025 Dokdo Toner"))
        ));

        assertThat(response.model()).isEqualTo("gpt-4o-mini-2024-07-18");
        assertThat(response.inputTokens()).isEqualTo(120);
        assertThat(response.outputTokens()).isEqualTo(80);
        ProductEnrichmentResult.Product product = response.result().products().getFirst();
        assertThat(product.displayBrand()).isEqualTo("라운드랩");
        assertThat(product.ingredients()).singleElement().satisfies(ingredient -> {
            assertThat(ingredient.name()).isEqualTo("정제수");
            assertThat(ingredient.riskScore()).isEqualTo(1);
            assertThat(ingredient.skinBenefits()).containsExactly("피부 보습");
        });
        server.verify();
    }

    private String response() throws Exception {
        String content = """
                {"products":[{"requestKey":"request-1","displayBrand":"라운드랩","displayProductName":"1025 독도 토너","resolutionConfidence":0.96,"ingredients":[{"order":1,"name":"정제수","purposes":["용제"],"skinBenefits":["피부 보습"],"riskScore":1,"caution20":false,"allergen":false}]}]}
                """.trim();
        return """
                {
                  "model": "gpt-4o-mini-2024-07-18",
                  "usage": {"prompt_tokens": 120, "completion_tokens": 80},
                  "choices": [{
                    "message": {
                      "content": %s
                    }
                  }]
                }
                """.formatted(new ObjectMapper().writeValueAsString(content));
    }
}
