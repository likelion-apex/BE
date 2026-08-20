package domain.inventory.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class InventoryAiJsonSupport {

    private static final Pattern RETRY_SECONDS = Pattern.compile(
            "(?:retry in|retry after)\\s+(\\d+(?:\\.\\d+)?)s", Pattern.CASE_INSENSITIVE);
    private static final Pattern RETRY_DELAY_FIELD = Pattern.compile(
            "\"retryDelay\"\\s*:\\s*\"(\\d+(?:\\.\\d+)?)s", Pattern.CASE_INSENSITIVE);

    private InventoryAiJsonSupport() {
    }

    /**
     * OpenAI/Gemini/Groq 등 AI provider 호출에서 발생한 {@link RestClientException}을
     * {@link AiProviderUnavailableException}으로 변환한다. 429는 할당량 소진으로, 그 외 4xx는
     * 요청 거부로, 나머지(5xx/타임아웃/연결 실패 등)는 일반 장애로 분류하며, 어느 경우든 예외가
     * 즉시(재시도 대기 없이) 던져지므로 호출측 오케스트레이터가 바로 다음 provider로 넘어간다.
     * 429는 응답의 {@code Retry-After} 헤더나 바디의 {@code retryDelay} 필드를 파싱해서 실제
     * 서버가 알려준 시간만큼만 쿨다운하도록 {@link AiProviderUnavailableException#getRetryAfter()}에
     * 담아 전달한다(값을 찾지 못하면 {@link AiProviderSkipGate}가 provider별 기본값으로 폴백한다).
     */
    public static AiProviderUnavailableException mapToUnavailable(
            String providerLabel, RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            if (responseException.getStatusCode().value() == 429) {
                Duration retryAfter = retryAfterDelay(responseException);
                return AiProviderUnavailableException.quota(
                        providerLabel + " 호출에 실패했습니다.", exception, retryAfter);
            }
            if (responseException.getStatusCode().is4xxClientError()) {
                return new AiProviderUnavailableException(providerLabel + " 요청이 거부되었습니다.", exception);
            }
        }
        return new AiProviderUnavailableException(providerLabel + " 호출에 실패했습니다.", exception);
    }

    private static Duration retryAfterDelay(RestClientResponseException exception) {
        Duration headerDelay = retryAfterHeader(exception.getResponseHeaders());
        if (headerDelay != null) {
            return headerDelay;
        }
        String body = exception.getResponseBodyAsString();
        if (body != null) {
            Duration bodyDelay = matchDelay(body, RETRY_SECONDS);
            if (bodyDelay == null) {
                bodyDelay = matchDelay(body, RETRY_DELAY_FIELD);
            }
            if (bodyDelay != null) {
                return bodyDelay;
            }
        }
        return null;
    }

    private static Duration matchDelay(String body, Pattern pattern) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            return null;
        }
        double seconds = Double.parseDouble(matcher.group(1));
        return Duration.ofMillis(Math.max(1L, (long) Math.ceil(seconds * 1_000)));
    }

    private static Duration retryAfterHeader(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Math.max(0L, Long.parseLong(value.trim())));
        } catch (NumberFormatException ignored) {
            return null;
        }
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
            List<String> purposes = stringList(node.path("purposes"));
            List<String> efficacyTags = stringList(node.path("efficacyTags"));
            String riskLevel = textOrNull(node.path("riskLevel"));
            result.put(name, new IngredientAiDetail(purposes, efficacyTags, riskLevel));
        });
        return result;
    }

    private static List<String> stringList(JsonNode arrayNode) {
        if (!arrayNode.isArray()) {
            return new ArrayList<>();
        }
        List<String> values = new ArrayList<>();
        arrayNode.forEach(itemNode -> {
            String value = textOrNull(itemNode);
            if (value != null) {
                values.add(value.trim());
            }
        });
        return values;
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
