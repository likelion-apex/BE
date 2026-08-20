package domain.inventory.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class InventoryAiJsonSupportTest {

    @Test
    void parsesStringAndObjectIngredientNames() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putArray("ingredients")
                .add("정제수")
                .addObject().put("name", "글리세린");

        assertThat(InventoryAiJsonSupport.parseIngredientNames(payload))
                .containsExactly("정제수", "글리세린");
    }

    @Test
    void parseIngredientDetails_parsesPurposesAndEfficacyTags() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode ingredientNode = payload.putArray("ingredients").addObject();
        ingredientNode.put("name", "정제수");
        ingredientNode.putArray("purposes").add("용제");
        ingredientNode.putArray("efficacyTags").add("피부 보습").add("피부 보호");
        ingredientNode.put("riskLevel", "LOW");

        var result = InventoryAiJsonSupport.parseIngredientDetails(payload);

        IngredientAiDetail detail = result.get("정제수");
        assertThat(detail.purposes()).containsExactly("용제");
        assertThat(detail.efficacyTags()).containsExactly("피부 보습", "피부 보호");
        assertThat(detail.riskLevel()).isEqualTo("LOW");
    }

    @Test
    void parseIngredientDetails_efficacyTagsDefaultToEmptyListWhenMissing() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode ingredientNode = payload.putArray("ingredients").addObject();
        ingredientNode.put("name", "정제수");
        ingredientNode.putArray("purposes").add("용제");
        ingredientNode.put("riskLevel", "LOW");

        var result = InventoryAiJsonSupport.parseIngredientDetails(payload);

        assertThat(result.get("정제수").efficacyTags()).isEmpty();
    }

    @Test
    void parseBrand_returnsValueWhenPresent() {
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("brand", "이니스프리");

        assertThat(InventoryAiJsonSupport.parseBrand(payload)).isEqualTo("이니스프리");
    }

    @Test
    void parseBrand_returnsNullWhenMissingBlankOrNullPayload() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode emptyBrand = objectMapper.createObjectNode().put("brand", "  ");
        ObjectNode missingBrand = objectMapper.createObjectNode();

        assertThat(InventoryAiJsonSupport.parseBrand(null)).isNull();
        assertThat(InventoryAiJsonSupport.parseBrand(emptyBrand)).isNull();
        assertThat(InventoryAiJsonSupport.parseBrand(missingBrand)).isNull();
    }

    @Test
    void mapToUnavailable_tooManyRequests_marksQuotaExceeded() {
        AiProviderUnavailableException result = InventoryAiJsonSupport.mapToUnavailable(
                "OpenAI", httpError(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(result.isQuotaExceeded()).isTrue();
        assertThat(result.getMessage()).isEqualTo("OpenAI 호출에 실패했습니다.");
        assertThat(result.getRetryAfter()).isNull();
    }

    @Test
    void mapToUnavailable_tooManyRequests_parsesRetryAfterHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "30");
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", headers, new byte[0], StandardCharsets.UTF_8);

        AiProviderUnavailableException result = InventoryAiJsonSupport.mapToUnavailable("OpenAI", exception);

        assertThat(result.isQuotaExceeded()).isTrue();
        assertThat(result.getRetryAfter()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void mapToUnavailable_tooManyRequests_parsesGeminiRetryDelayBody() {
        String body = "{\"error\":{\"code\":429,\"message\":\"quota\",\"details\":[{\"retryDelay\":\"12s\"}]}}";
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", new HttpHeaders(),
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        AiProviderUnavailableException result = InventoryAiJsonSupport.mapToUnavailable("Gemini", exception);

        assertThat(result.isQuotaExceeded()).isTrue();
        assertThat(result.getRetryAfter()).isEqualTo(Duration.ofSeconds(12));
    }

    @Test
    void mapToUnavailable_otherClientError_marksRejectedWithoutQuota() {
        AiProviderUnavailableException result = InventoryAiJsonSupport.mapToUnavailable(
                "Groq", httpError(HttpStatus.BAD_REQUEST));

        assertThat(result.isQuotaExceeded()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Groq 요청이 거부되었습니다.");
    }

    @Test
    void mapToUnavailable_serverError_marksGenericUnavailableImmediately() {
        AiProviderUnavailableException result = InventoryAiJsonSupport.mapToUnavailable(
                "Gemini", HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                        new HttpHeaders(), new byte[0], StandardCharsets.UTF_8));

        assertThat(result.isQuotaExceeded()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Gemini 호출에 실패했습니다.");
    }

    @Test
    void mapToUnavailable_connectionOrTimeoutFailure_marksGenericUnavailableImmediately() {
        AiProviderUnavailableException result = InventoryAiJsonSupport.mapToUnavailable(
                "OpenAI", new ResourceAccessException("read timed out"));

        assertThat(result.isQuotaExceeded()).isFalse();
        assertThat(result.getMessage()).isEqualTo("OpenAI 호출에 실패했습니다.");
    }

    private HttpClientErrorException httpError(HttpStatus status) {
        return HttpClientErrorException.create(
                status, status.getReasonPhrase(), new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
    }
}
