package domain.inventory.client;

import domain.inventory.ai.AiProviderUnavailableException;
import domain.inventory.ai.InventoryAiJsonSupport;
import domain.inventory.ai.InventoryAiProperties;
import domain.member.SkinConcern;
import domain.member.SkinType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 제품명/전성분과 회원의 피부타입·피부고민을 바탕으로 ChatGPT에게 맞춤 분석(점수 + 근거 키워드)을 요청한다.
 */
@Slf4j
@Component
public class OpenAiPersonalizedAnalysisClient {

    public static final String SYSTEM_PROMPT = """
            당신은 화장품 성분을 바탕으로 사용자 피부에 대한 맞춤 분석을 제공하는 뷰티 전문가입니다.
            제품명, 전성분 목록, 사용자의 피부타입과 피부고민을 참고하여 이 제품이 사용자에게 얼마나 적합한지
            0에서 100 사이의 종합 점수와, 점수 판단 근거가 되는 키워드 3가지(각 키워드별 상세 이유 포함)를 제시하세요.
            전성분을 알 수 없으면 제품명과 일반적인 화장품 지식을 바탕으로 보수적으로 평가하세요.
            keywords 배열은 반드시 정확히 3개여야 하며, 각 항목의 keyword와 reason은 모두 비어 있으면 안 됩니다.
            빈 배열이나 3개 미만의 keywords는 절대 허용되지 않습니다.
            반드시 아래 JSON 형식으로만 답변하세요:
            {"score": 88, "keywords": [{"keyword": "...", "reason": "..."}, {"keyword": "...", "reason": "..."}, {"keyword": "...", "reason": "..."}]}
            """;

    private static final int MAX_KEYWORD_COUNT = 3;
    private static final String FALLBACK_REASON = "AI가 구체적인 이유를 제공하지 않았습니다.";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final InventoryAiProperties inventoryAiProperties;
    private final String apiKey;
    private final String model;
    private final String organizationId;

    public OpenAiPersonalizedAnalysisClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model}") String model,
            @Value("${openai.organization-id:}") String organizationId,
            @Qualifier("inventoryOpenAiRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            InventoryAiProperties inventoryAiProperties) {
        this.apiKey = apiKey;
        this.model = model;
        this.organizationId = organizationId;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
        this.inventoryAiProperties = inventoryAiProperties;
    }

    public PersonalizedAnalysisResult analyze(
            String productName, List<String> ingredientNames, SkinType skinType, Set<SkinConcern> skinConcerns) {
        if (productName == null || productName.isBlank()) {
            return null;
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiProviderUnavailableException("OPENAI_API_KEY가 없습니다.");
        }
        try {
            String requestJson = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "temperature", 0,
                    "max_tokens", inventoryAiProperties.getOpenaiMaxOutputTokens(),
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content",
                                    buildUserContent(productName, ingredientNames, skinType, skinConcerns))
                    )
            ));
            String responseJson = restClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .headers(headers -> {
                        if (organizationId != null && !organizationId.isBlank()) {
                            headers.set("OpenAI-Organization", organizationId);
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);
            JsonNode response = InventoryAiJsonSupport.readObject(objectMapper, responseJson);
            String content = response == null
                    ? null
                    : response.path("choices").path(0).path("message").path("content").asText(null);
            JsonNode parsed = InventoryAiJsonSupport.readObject(objectMapper, content);
            // null(유효 keyword가 하나도 없는 완전히 빈 응답)은 예외로 바꾸지 않고 그대로 반환한다.
            // 콘텐츠 품질 문제이지 provider 장애가 아니므로, 호출부가 쿨다운 없이 다음
            // provider로 넘어가도록 한다.
            return parseResult(parsed);
        } catch (AiProviderUnavailableException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("ChatGPT 맞춤 분석 실패: productName={}, message={}", productName, e.getMessage());
            throw InventoryAiJsonSupport.mapToUnavailable("OpenAI 맞춤 분석", e);
        } catch (RuntimeException e) {
            log.warn("ChatGPT 맞춤 분석 응답 파싱 실패: productName={}", productName);
            throw new AiProviderUnavailableException("OpenAI 맞춤 분석 응답을 해석할 수 없습니다.", e);
        }
    }

    public static String buildUserContent(
            String productName, List<String> ingredientNames, SkinType skinType, Set<SkinConcern> skinConcerns) {
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

    /**
     * score만 유효하면 keywords가 부족해도 성공으로 간주한다(실제 provider 네트워크 호출을 유발하는
     * 폴백은 진짜 장애 상황에만 남겨두기 위함). 단, 유효한 keyword가 단 하나도 없으면(완전히 빈 응답)
     * 이는 해당 provider가 사실상 응답을 만들어내지 못한 것으로 보고 null을 반환해 다음 provider로
     * 폴백시킨다("일부만 채워진 응답"과 "완전히 빈 응답"을 구분: 전자는 흔한 토큰 절삭 문제라 매번
     * 폴백하면 지연시간이 커지지만, 후자는 드물고 명확한 실패 신호라 한 번 더 시도할 가치가 있다).
     * keyword가 있는데 reason만 비어 있으면 항목을 통째로 버리지 않고 {@link #FALLBACK_REASON}으로
     * 채워 살린다(포맷 차이로 멀쩡한 keyword까지 사라져 0개가 되는 상황을 줄이기 위함).
     * {@value #MAX_KEYWORD_COUNT}개를 넘으면 앞에서부터 그만큼만 취한다. 1~2개로 부족한 경우는
     * 호출부(InventoryService)가 결정론적 기본 키워드로 채워 응답을 완성한다.
     */
    public static PersonalizedAnalysisResult parseResult(JsonNode parsed) {
        if (parsed == null || !parsed.isObject()) {
            return null;
        }
        int score = parsed.path("score").asInt(-1);
        if (score < 0) {
            return null;
        }
        List<PersonalizedAnalysisResult.Keyword> keywords = new ArrayList<>();
        JsonNode keywordsNode = parsed.path("keywords");
        if (keywordsNode.isArray()) {
            keywordsNode.forEach(node -> {
                String keyword = node.path("keyword").asText(null);
                if (keyword == null || keyword.isBlank()) {
                    return;
                }
                String reason = node.path("reason").asText(null);
                keywords.add(new PersonalizedAnalysisResult.Keyword(
                        keyword, reason == null || reason.isBlank() ? FALLBACK_REASON : reason));
            });
        }
        if (keywords.isEmpty()) {
            return null;
        }
        List<PersonalizedAnalysisResult.Keyword> limited = keywords.size() > MAX_KEYWORD_COUNT
                ? List.copyOf(keywords.subList(0, MAX_KEYWORD_COUNT))
                : List.copyOf(keywords);
        return new PersonalizedAnalysisResult(Math.min(score, 100), limited);
    }
}
