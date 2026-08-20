package domain.inventory.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class InventoryAiJsonSupport {

    private InventoryAiJsonSupport() {
    }

    /**
     * OpenAI/Gemini/Groq 등 AI provider 호출에서 발생한 {@link RestClientException}을
     * {@link AiProviderUnavailableException}으로 변환한다. 429는 할당량 소진으로, 그 외 4xx는
     * 요청 거부로, 나머지(5xx/타임아웃/연결 실패 등)는 일반 장애로 분류하며, 어느 경우든 예외가
     * 즉시(재시도 대기 없이) 던져지므로 호출측 오케스트레이터가 바로 다음 provider로 넘어간다.
     */
    public static AiProviderUnavailableException mapToUnavailable(
            String providerLabel, RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            if (responseException.getStatusCode().value() == 429) {
                return AiProviderUnavailableException.quota(providerLabel + " 호출에 실패했습니다.", exception);
            }
            if (responseException.getStatusCode().is4xxClientError()) {
                return new AiProviderUnavailableException(providerLabel + " 요청이 거부되었습니다.", exception);
            }
        }
        return new AiProviderUnavailableException(providerLabel + " 호출에 실패했습니다.", exception);
    }

    static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    public static List<String> parseIngredientNames(JsonNode payload) {
        if (payload == null) {
            return List.of();
        }
        JsonNode ingredientsNode = payload.path("ingredients");
        if (!ingredientsNode.isArray()) {
            return List.of();
        }
        List<String> ingredients = new ArrayList<>();
        ingredientsNode.forEach(node -> {
            String name = ingredientName(node);
            if (name != null) {
                ingredients.add(name);
            }
        });
        return ingredients;
    }

    public static String parseBrand(JsonNode payload) {
        if (payload == null) {
            return null;
        }
        return textOrNull(payload.path("brand"));
    }

    public static Map<String, IngredientAiDetail> parseIngredientDetails(JsonNode payload) {
        if (payload == null) {
            return Map.of();
        }
        JsonNode ingredientsNode = payload.path("ingredients");
        if (!ingredientsNode.isArray()) {
            return Map.of();
        }
        Map<String, IngredientAiDetail> result = new LinkedHashMap<>();
        ingredientsNode.forEach(node -> {
            String name = ingredientName(node);
            if (name == null) {
                return;
            }
            List<String> purposes = new ArrayList<>();
            JsonNode purposesNode = node.path("purposes");
            if (purposesNode.isArray()) {
                purposesNode.forEach(purposeNode -> {
                    String value = textOrNull(purposeNode);
                    if (value != null) {
                        purposes.add(value.trim());
                    }
                });
            }
            String riskLevel = textOrNull(node.path("riskLevel"));
            result.put(name, new IngredientAiDetail(purposes, riskLevel));
        });
        return result;
    }

    private static String ingredientName(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return textOrNull(node);
        }
        String name = textOrNull(node.path("name"));
        if (name != null) {
            return name.trim();
        }
        name = textOrNull(node.path("ingredientName"));
        return name == null ? null : name.trim();
    }

    public static JsonNode readObject(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        String trimmed = json.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            trimmed = trimmed.substring(start, end + 1);
        }
        return objectMapper.readTree(trimmed);
    }
}
