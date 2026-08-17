package domain.inventory.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class InventoryAiJsonSupport {

    private InventoryAiJsonSupport() {
    }

    static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    public static List<String> parseIngredientNames(JsonNode payload) {
        JsonNode ingredientsNode = payload.path("ingredients");
        if (!ingredientsNode.isArray()) {
            return List.of();
        }
        List<String> ingredients = new ArrayList<>();
        ingredientsNode.forEach(node -> {
            String name = node.asText(null);
            if (name != null && !name.isBlank()) {
                ingredients.add(name.trim());
            }
        });
        return ingredients;
    }

    public static Map<String, List<String>> parsePurposes(JsonNode payload) {
        JsonNode ingredientsNode = payload.path("ingredients");
        if (!ingredientsNode.isArray()) {
            return Map.of();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        ingredientsNode.forEach(node -> {
            String name = node.path("name").asText(null);
            if (name == null || name.isBlank()) {
                return;
            }
            List<String> purposes = new ArrayList<>();
            JsonNode purposesNode = node.path("purposes");
            if (purposesNode.isArray()) {
                purposesNode.forEach(purposeNode -> {
                    String value = purposeNode.asText(null);
                    if (value != null && !value.isBlank()) {
                        purposes.add(value.trim());
                    }
                });
            }
            result.put(name.trim(), purposes);
        });
        return result;
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
