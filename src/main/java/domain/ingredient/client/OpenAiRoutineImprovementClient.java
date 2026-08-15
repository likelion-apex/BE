package domain.ingredient.client;

import domain.ingredient.client.RoutineImprovementResult.Match;
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
 * 대상 제품(전성분)과 사용자가 보유한 인벤토리 목록을 바탕으로 루틴 개선 방향 제시(4.6) -
 * 시너지/충돌/중복 관계를 ChatGPT에게 요청한다. 이 API의 목적 자체가 AI 분석 결과이므로,
 * 실패 시 다른 클라이언트와 달리 null을 반환해 호출측에서 에러로 처리하도록 한다.
 * 응답에 포함된 productId가 후보 목록(candidates)에 없으면 방어적으로 해당 관계를 제거한다
 * (모델 환각 대비).
 */
@Slf4j
@Component
public class OpenAiRoutineImprovementClient {

    private static final String SYSTEM_PROMPT = """
            당신은 화장품 성분/제품 조합을 분석하는 뷰티 전문가입니다.
            사용자가 확인하려는 대상 제품의 이름과 추정 전성분, 그리고 사용자가 이미 보유한 제품 목록(id, 이름, 카테고리)을
            참고하여 아래 3가지 관계를 분석하세요.
            - synergy: 보유 제품 중 대상 제품과 함께 쓰면 성분 시너지로 효과가 좋아지는 제품이 있는지
            - conflict: 보유 제품 중 대상 제품과 함께 쓰면 성분 충돌로 피부 자극 등 부작용 우려가 있는 제품이 있는지
            - duplicate: 보유 제품 중 대상 제품과 핵심 효능/성분이 거의 동일해 새로 살 필요가 없는 제품이 있는지
            각 관계마다 보유 제품 목록 중 가장 확실한 것 최대 1개만 고르고, 해당하는 제품이 없으면 null로 응답하세요.
            productId는 반드시 주어진 보유 제품 목록의 id 중에서만 선택해야 합니다. 목록에 없는 id를 만들어내지 마세요.
            message는 사용자에게 보여줄 친근한 한국어 한 문장으로 작성하세요
            (예: "보유하신 에스트라 로션과 환상의 짝꿍", "보유하신 AHA 각질 토너와는 상극!", "이미 거의 똑같은 앰플을 갖고 계세요").
            전성분을 알 수 없으면 제품명과 일반적인 화장품 지식을 바탕으로 보수적으로 판단하세요.
            반드시 아래 JSON 형식으로만 답변하세요:
            {"synergy": {"productId": 11, "message": "..."}, "conflict": null, "duplicate": null}
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String organizationId;

    public OpenAiRoutineImprovementClient(
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

    public RoutineImprovementResult analyze(
            String productName, List<String> ingredientNames, List<OwnedProductCandidate> candidates) {
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
                            Map.of("role", "user", "content", buildUserContent(productName, ingredientNames, candidates))
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

            return parseResult(response, productName, candidates);
        } catch (RestClientException e) {
            log.warn("ChatGPT 루틴 개선 분석 실패: productName={}, message={}", productName, e.getMessage());
            return null;
        }
    }

    private String buildUserContent(String productName, List<String> ingredientNames, List<OwnedProductCandidate> candidates) {
        String ingredients = (ingredientNames == null || ingredientNames.isEmpty())
                ? "알 수 없음"
                : String.join(", ", ingredientNames);
        String ownedList = candidates.stream()
                .map(candidate -> "id=%d, name=%s, category=%s".formatted(
                        candidate.productId(), candidate.productName(),
                        candidate.category() == null ? "미분류" : candidate.category().name()))
                .collect(Collectors.joining("\n"));

        return """
                대상 제품명: %s
                대상 제품 추정 전성분: %s
                보유 제품 목록:
                %s
                """.formatted(productName, ingredients, ownedList);
    }

    private RoutineImprovementResult parseResult(JsonNode response, String productName, List<OwnedProductCandidate> candidates) {
        if (response == null) {
            return null;
        }
        String content = response.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(content);
            Set<Long> validIds = candidates.stream().map(OwnedProductCandidate::productId).collect(Collectors.toSet());
            Match synergy = parseMatch(parsed.path("synergy"), validIds);
            Match conflict = parseMatch(parsed.path("conflict"), validIds);
            Match duplicate = parseMatch(parsed.path("duplicate"), validIds);
            return new RoutineImprovementResult(synergy, conflict, duplicate);
        } catch (Exception e) {
            log.warn("ChatGPT 루틴 개선 분석 응답 파싱 실패: productName={}, content={}", productName, content);
            return null;
        }
    }

    private Match parseMatch(JsonNode node, Set<Long> validIds) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        long productId = node.path("productId").asLong(-1);
        String message = node.path("message").asText(null);
        if (productId < 0 || message == null || message.isBlank() || !validIds.contains(productId)) {
            return null;
        }
        return new Match(productId, message);
    }
}
