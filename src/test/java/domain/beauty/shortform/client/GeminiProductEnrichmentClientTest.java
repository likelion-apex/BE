package domain.beauty.shortform.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import domain.beauty.config.GeminiProperties;
import domain.beauty.shortform.config.ShortformProductEnrichmentProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GeminiProductEnrichmentClientTest {

    @Test
    void searchesWithStructuredOutputAndParsesInlineCitationSources() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        GeminiProperties geminiProperties = new GeminiProperties();
        geminiProperties.setApiKey("test-gemini-key");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiProductEnrichmentClient client = new GeminiProductEnrichmentClient(
                builder.build(),
                geminiProperties,
                new ShortformProductEnrichmentProperties(),
                new GeminiProductEnrichmentPromptResources(objectMapper),
                objectMapper
        );
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-gemini-key"))
                .andExpect(content().string(containsString("google_search")))
                .andExpect(content().string(containsString("request-1")))
                .andRespond(withSuccess(response(objectMapper), MediaType.APPLICATION_JSON));

        ProductEnrichmentResult.Response response = client.enrich(new ProductEnrichmentInput(
                true,
                List.of(new ProductEnrichmentInput.Product(
                        "request-1", "수딩 크림", "Torriden", "DIVE IN Soothing Cream", null,
                        "Torriden DIVE IN Soothing Cream"))
        ));

        assertThat(response.model()).isEqualTo("gemini-3.6-flash");
        assertThat(response.webSearchCalls()).isEqualTo(1);
        assertThat(response.webSources()).singleElement()
                .satisfies(source -> assertThat(source.url()).contains("torriden.com"));
        assertThat(response.result().products()).singleElement().satisfies(product -> {
            assertThat(product.lookupStatus()).isEqualTo(ProductEnrichmentResult.LookupStatus.ESTIMATED);
            assertThat(product.ingredients()).hasSize(2);
        });
        server.verify();
    }

    @Test
    void retriesOnceWhenGeminiReturnsRateLimit() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        GeminiProperties geminiProperties = new GeminiProperties();
        geminiProperties.setApiKey("test-gemini-key");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiProductEnrichmentClient client = new GeminiProductEnrichmentClient(
                builder.build(), geminiProperties, new ShortformProductEnrichmentProperties(),
                new GeminiProductEnrichmentPromptResources(objectMapper), objectMapper);
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "0"));
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andRespond(withSuccess(response(objectMapper), MediaType.APPLICATION_JSON));

        ProductEnrichmentResult.Response response = client.enrich(new ProductEnrichmentInput(
                true,
                List.of(new ProductEnrichmentInput.Product(
                        "request-1", "수딩 크림", "Torriden", "DIVE IN Soothing Cream", null,
                        "Torriden DIVE IN Soothing Cream"))));

        assertThat(response.result().products()).hasSize(1);
        server.verify();
    }

    @Test
    void supportsSourceLessKnowledgeFallbackAsExplicitEstimate() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        GeminiProperties geminiProperties = new GeminiProperties();
        geminiProperties.setApiKey("test-gemini-key");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiProductEnrichmentClient client = new GeminiProductEnrichmentClient(
                builder.build(), geminiProperties, new ShortformProductEnrichmentProperties(),
                new GeminiProductEnrichmentPromptResources(objectMapper), objectMapper);
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andExpect(content().string(containsString("모델 지식으로 보완")))
                .andRespond(withSuccess(knowledgeResponse(objectMapper), MediaType.APPLICATION_JSON));

        ProductEnrichmentResult.Response response = client.enrichWithoutSearch(new ProductEnrichmentInput(
                true,
                List.of(new ProductEnrichmentInput.Product(
                        "request-1", "수딩 크림", "Torriden", "DIVE IN Soothing Cream", null,
                        "Torriden DIVE IN Soothing Cream"))));

        assertThat(response.model()).endsWith("-knowledge-fallback");
        assertThat(response.webSearchCalls()).isZero();
        assertThat(response.webSources()).isEmpty();
        assertThat(response.result().products().getFirst().lookupStatus())
                .isEqualTo(ProductEnrichmentResult.LookupStatus.ESTIMATED);
        assertThat(response.result().products().getFirst().resolutionConfidence()).isEqualTo(0.84);
        assertThat(response.result().products().getFirst().sources()).isEmpty();
        server.verify();
    }

    private String response(ObjectMapper objectMapper) throws Exception {
        String result = """
                {"products":[{"requestKey":"request-1","displayBrand":"토리든","displayProductName":"다이브인 저분자 히알루론산 수딩 크림","marketOrVariant":"한국 판매 처방","lookupStatus":"ESTIMATED","resolutionConfidence":0.78,"notes":"영상 원문과 공식 페이지를 연결한 추정","sources":[{"url":"https://torriden.com/product/dive-in-soothing-cream","title":"DIVE IN Soothing Cream","sourceType":"OFFICIAL"}],"ingredients":[{"order":1,"name":"정제수","purposes":["용제"],"skinBenefits":["수분 공급"],"riskScore":1,"caution20":false,"allergen":false},{"order":2,"name":"부틸렌글라이콜","purposes":["보습제"],"skinBenefits":["피부 보습"],"riskScore":1,"caution20":false,"allergen":false}]}]}
                """.trim();
        return """
                {
                  "status":"completed",
                  "model":"gemini-3.6-flash",
                  "usage":{"total_input_tokens":90,"total_output_tokens":120},
                  "steps":[
                    {"type":"google_search_call","queries":["토리든 다이브인 수딩크림 전성분"]},
                    {"type":"model_output","content":[{"type":"text","text":%s,"annotations":[{"type":"url_citation","uri":"https://torriden.com/product/dive-in-soothing-cream","title":"DIVE IN Soothing Cream"}]}]}
                  ]
                }
                """.formatted(objectMapper.writeValueAsString(result));
    }

    private String knowledgeResponse(ObjectMapper objectMapper) throws Exception {
        String result = """
                {"products":[{"requestKey":"request-1","displayBrand":"토리든","displayProductName":"다이브인 저분자 히알루론산 수딩 크림","marketOrVariant":"처방 추정","lookupStatus":"FOUND","resolutionConfidence":0.98,"notes":"모델 지식 기반 추정","sources":[{"url":"https://example.com/unverified","title":"unverified","sourceType":"OTHER"}],"ingredients":[{"order":1,"name":"정제수","purposes":["용제"],"skinBenefits":["수분 공급"],"riskScore":1,"caution20":false,"allergen":false}]}]}
                """.trim();
        return """
                {"status":"completed","model":"gemini-3.6-flash","usage":{"total_input_tokens":50,"total_output_tokens":70},"steps":[{"type":"model_output","content":[{"type":"text","text":%s}]}]}
                """.formatted(objectMapper.writeValueAsString(result));
    }
}
