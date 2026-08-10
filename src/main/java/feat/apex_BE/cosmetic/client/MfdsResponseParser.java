package feat.apex_BE.cosmetic.client;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 식약처 공공데이터 API(공통 응답 포맷: header/body/items/item)를 파싱하기 위한 공통 유틸.
 * 결과가 1건일 때는 item이 객체로, 여러 건일 때는 배열로 내려오는 특성을 함께 처리한다.
 */
final class MfdsResponseParser {

    private MfdsResponseParser() {
    }

    static List<JsonNode> extractItems(JsonNode response) {
        if (response == null) {
            return List.of();
        }
        JsonNode itemsNode = response.path("body").path("items");
        if (itemsNode.isMissingNode() || itemsNode.isNull()) {
            return List.of();
        }
        // 이 두 API는 items가 배열로 바로 내려오지만(item 래퍼 없음), 다른 식약처 API들은
        // items.item 형태(1건이면 객체, 여러 건이면 배열)로 내려오는 경우가 있어 둘 다 처리한다.
        if (itemsNode.isArray()) {
            return toList(itemsNode);
        }
        if (itemsNode.isObject()) {
            JsonNode item = itemsNode.path("item");
            if (item.isMissingNode() || item.isNull()) {
                return List.of();
            }
            return item.isArray() ? toList(item) : List.of(item);
        }
        return List.of();
    }

    private static List<JsonNode> toList(JsonNode arrayNode) {
        List<JsonNode> list = new ArrayList<>();
        arrayNode.forEach(list::add);
        return list;
    }

    static int extractTotalCount(JsonNode response) {
        if (response == null) {
            return 0;
        }
        return response.path("body").path("totalCount").asInt(0);
    }

    static List<String> splitCountries(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }
}
