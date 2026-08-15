package domain.ingredient.client;

import domain.ingredient.domain.AnalysisGrade;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 제품명/전성분과 회원의 피부타입·피부고민을 바탕으로 ChatGPT에게 AI 루틴 분석(4.5) - 등급(SAFE/MEH/GOOD/RISK)과
 * 근거 코멘트 한 줄을 요청한다. 이 API의 목적 자체가 AI 분석 결과이므로, 실패 시 null을 반환해 호출측에서
 * 에러로 처리하도록 한다.
 */
@Slf4j
@Component
public class OpenAiGradeAnalysisClient {

    private static final String SYSTEM_PROMPT = """
            당신은 화장품 성분을 바탕으로 사용자 피부에 대한 맞춤 분석을 제공하는 뷰티 전문가입니다.
            제품명, 전성분 목록, 사용자의 피부타입과 피부고민을 참고하여 이 제품이 사용자에게 얼마나 적합한지
            아래 4가지 등급 중 하나로 판정하고, 판정 근거를 한 문장의 친근한 코멘트로 제시하세요.
            - SAFE: 안전 (순하고 자극 우려가 적음)
            - MEH: 아쉬움 (나쁘지 않지만 사용자에게 큰 이점은 없음)
            - GOOD: 좋음 (사용자 피부타입/고민에 도움이 됨)
            - RISK: 위험 (사용자 피부타입/고민에 자극이나 부작용 우려가 있음)
            전성분을 알 수 없으면 제품명과 일반적인 화장품 지식을 바탕으로 보수적으로 평가하세요.
            반드시 아래 JSON 형식으로만 답변하세요:
            {"grade": "GOOD", "comment": "..."}
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String organizationId;

    public OpenAiGradeAnalysisClient(
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

    public GradeAnalysisResult analyze(
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
            log.warn("ChatGPT AI 루틴 분석 실패: productName={}, message={}", productName, e.getMessage());
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

    private GradeAnalysisResult parseResult(JsonNode response, String productName) {
        if (response == null) {
            return null;
        }
        String content = response.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(content);
            String gradeText = parsed.path("grade").asText(null);
            String comment = parsed.path("comment").asText(null);
            if (gradeText == null || comment == null || comment.isBlank()) {
                return null;
            }
            AnalysisGrade grade = parseGrade(gradeText);
            if (grade == null) {
                return null;
            }
            return new GradeAnalysisResult(grade, comment);
        } catch (Exception e) {
            log.warn("ChatGPT AI 루틴 분석 응답 파싱 실패: productName={}, content={}", productName, content);
            return null;
        }
    }

    private AnalysisGrade parseGrade(String gradeText) {
        try {
            return AnalysisGrade.valueOf(gradeText.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
