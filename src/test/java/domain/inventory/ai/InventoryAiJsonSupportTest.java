package domain.inventory.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class InventoryAiJsonSupportTest {

    @Test
    void parsesStringAndObjectIngredientNames() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putArray("ingredients")
                .add("정제수")
                .addObject().put("name", "글리세린");

        assertThat(InventoryAiJsonSupport.parseIngredientNames(payload))
                .containsExactly("정제수", "글리세린");
    }

    @Test
    void parseBrand_returnsValueWhenPresent() {
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("brand", "이니스프리");

        assertThat(InventoryAiJsonSupport.parseBrand(payload)).isEqualTo("이니스프리");
    }

    @Test
    void parseBrand_returnsNullWhenMissingBlankOrNullPayload() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode emptyBrand = objectMapper.createObjectNode().put("brand", "  ");
        ObjectNode missingBrand = objectMapper.createObjectNode();

        assertThat(InventoryAiJsonSupport.parseBrand(null)).isNull();
        assertThat(InventoryAiJsonSupport.parseBrand(emptyBrand)).isNull();
        assertThat(InventoryAiJsonSupport.parseBrand(missingBrand)).isNull();
    }
}
