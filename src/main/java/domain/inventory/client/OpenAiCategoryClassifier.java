package domain.inventory.client;

import domain.inventory.ProductCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * 제품명으로 ChatGPT(OpenAI Chat Completions) API를 호출해 화장품 카테고리를 자동 분류한다.
 * 인벤토리에 신규 상품이 처음 등록될 때 한 번만 호출되며, 결과는 Product에 저장되어 이후 재사용된다.
 *
 * 1) AI 응답 성공 → AI가 제품명을 보고 9종 중 실제로 가장 가까운 것을 스스로 판단해서 반환
 * 2) API 장애/파싱 실패 등 응답 자체를 못 받은 경우 → 어떤 제품인지와 무관하게 고정값(DEFAULT_CATEGORY)을 그대로 반환
 */
@Slf4j
@Component
public class OpenAiCategoryClassifier {

    /** API 장애 등으로 분류 자체가 불가능할 때만 쓰는 고정 기본값. */
    private static final ProductCategory DEFAULT_CATEGORY = ProductCategory.SKIN_TONER;

    private static final String SYSTEM_PROMPT = """
            당신은 화장품을 카테고리로 분류하는 어시스턴트입니다.
            사용자가 알려준 화장품 제품명을 보고 다음 9개 카테고리 중 가장 가까운 것 하나로 반드시 분류하세요:
            SKIN_TONER(스킨/토너), SKIN_TONER_PAD(스킨/토너 패드), LOTION_EMULSION(로션/에멀전),
            ESSENCE_AMPOULE_SERUM(에센스/앰플/세럼), FACE_OIL(페이스오일), CREAM(크림),
            EYE_CARE(아이케어), MIST_GEL(미스트·젤), BALM_MULTIBALM(밤/멀티밤)
            확신이 없더라도 위 9개 중 가장 근접한 카테고리를 반드시 선택하세요. null이나 그 외 값은 절대 반환하지 마세요.
            반드시 아래 JSON 형식으로만 답변하세요: {"category": "CREAM"}
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String organizationId;

    public OpenAiCategoryClassifier(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.api-url}") String apiUrl,
            @Value("${openai.model}") String model,
            @Value("${openai.organization-id:}") String organizationId,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.organizationId = organizationId;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(apiUrl).build();
    }

    /** 분류에 성공하면 AI가 고른 카테고리, API 장애 등으로 실패하면 DEFAULT_CATEGORY를 반환해요. */
    public ProductCategory classify(String productName) {
        if (apiKey == null || apiKey.isBlank() || productName == null || productName.isBlank()) {
            return DEFAULT_CATEGORY;
        }
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "temperature", 0,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", "제품명: " + productName)
                    )
            );

            JsonNode response = restClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .headers(headers -> {
                        if (organizationId != null && !organizationId.isBlank()) {
                            headers.set("OpenAI-Organization", organizationId);
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            return parseCategory(response, productName);
        } catch (RestClientException e) {
            log.warn("ChatGPT 카테고리 분류 실패, 기본값({})으로 대체: productName={}, message={}",
                    DEFAULT_CATEGORY, productName, e.getMessage());
            return DEFAULT_CATEGORY;
        }
    }

    private ProductCategory parseCategory(JsonNode response, String productName) {
        if (response == null) {
            return DEFAULT_CATEGORY;
        }
        String content = response.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            return DEFAULT_CATEGORY;
        }
        try {
            JsonNode parsed = objectMapper.readTree(content);
            String category = parsed.path("category").asText(null);
            if (category == null || category.isBlank()) {
                return DEFAULT_CATEGORY;
            }
            return ProductCategory.valueOf(category.trim().toUpperCase());
        } catch (Exception e) {
            log.warn("ChatGPT 카테고리 분류 응답 파싱 실패, 기본값({})으로 대체: productName={}, content={}",
                    DEFAULT_CATEGORY, productName, content);
            return DEFAULT_CATEGORY;
        }
    }
}
