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
