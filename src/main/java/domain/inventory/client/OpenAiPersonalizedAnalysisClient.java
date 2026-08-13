package domain.inventory.client;

import domain.member.SkinConcern;
import domain.member.SkinType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 제품명/전성분과 회원의 피부타입·피부고민을 바탕으로 ChatGPT에게 맞춤 분석(점수 + 근거 키워드)을 요청한다.
 * 이 API의 목적 자체가 AI 분석 결과이므로, 실패 시 다른 클라이언트와 달리 null을 반환해 호출측에서
 * 에러로 처리하도록 한다.
 */
@Slf4j
@Component
public class OpenAiPersonalizedAnalysisClient {

    private static final String SYSTEM_PROMPT = """
            당신은 화장품 성분을 바탕으로 사용자 피부에 대한 맞춤 분석을 제공하는 뷰티 전문가입니다.
            제품명, 전성분 목록, 사용자의 피부타입과 피부고민을 참고하여 이 제품이 사용자에게 얼마나 적합한지
            0에서 100 사이의 종합 점수와, 점수 판단 근거가 되는 키워드 3가지(각 키워드별 상세 이유 포함)를 제시하세요.
            전성분을 알 수 없으면 제품명과 일반적인 화장품 지식을 바탕으로 보수적으로 평가하세요.
            반드시 아래 JSON 형식으로만 답변하세요:
            {"score": 88, "keywords": [{"keyword": "...", "reason": "..."}, {"keyword": "...", "reason": "..."}, {"keyword": "...", "reason": "..."}]}
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String organizationId;

    public OpenAiPersonalizedAnalysisClient(
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

    public PersonalizedAnalysisResult analyze(
            String productName, List<String> ingredientNames, SkinType skinType, Set<SkinConcern> skinConcerns) {
        if (apiKey == null || apiKey.isBlank() || productName == null || productName.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "temperature", 0,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", buildUserContent(productName, ingredientNames, skinType, skinConcerns))
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

            return parseResult(response, productName);
        } catch (RestClientException e) {
            log.warn("ChatGPT 맞춤 분석 실패: productName={}, message={}", productName, e.getMessage());
            return null;
        }
    }

    private String buildUserContent(String productName, List<String> ingredientNames, SkinType skinType, Set<SkinConcern> skinConcerns) {
        String ingredients = (ingredientNames == null || ingredientNames.isEmpty())
                ? "알 수 없음"
                : String.join(", ", ingredientNames);
        String skinTypeLabel = skinType != null ? skinType.getLabel() : "미입력";
        String skinConcernLabels = (skinConcerns == null || skinConcerns.isEmpty())
                ? "미입력"
                : skinConcerns.stream().map(SkinConcern::getLabel).collect(Collectors.joining(", "));

        return """
                제품명: %s
                전성분: %s
                사용자 피부타입: %s
                사용자 피부고민: %s
                """.formatted(productName, ingredients, skinTypeLabel, skinConcernLabels);
    }

    private PersonalizedAnalysisResult parseResult(JsonNode response, String productName) {
        if (response == null) {
            return null;
        }
        String content = response.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(content);
            int score = parsed.path("score").asInt(-1);
            if (score < 0) {
                return null;
            }
            List<PersonalizedAnalysisResult.Keyword> keywords = new ArrayList<>();
            JsonNode keywordsNode = parsed.path("keywords");
            if (keywordsNode.isArray()) {
                keywordsNode.forEach(node -> {
                    String keyword = node.path("keyword").asText(null);
                    String reason = node.path("reason").asText(null);
                    if (keyword != null && !keyword.isBlank()) {
                        keywords.add(new PersonalizedAnalysisResult.Keyword(keyword, reason));
                    }
                });
            }
            return new PersonalizedAnalysisResult(Math.min(score, 100), keywords);
        } catch (Exception e) {
            log.warn("ChatGPT 맞춤 분석 응답 파싱 실패: productName={}, content={}", productName, content);
            return null;
        }
    }
}
