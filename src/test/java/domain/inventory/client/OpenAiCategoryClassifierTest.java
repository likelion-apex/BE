package domain.inventory.client;

import static org.assertj.core.api.Assertions.assertThat;

import domain.inventory.ProductCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class OpenAiCategoryClassifierTest {

    private OpenAiCategoryClassifier classifier;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        classifier = new OpenAiCategoryClassifier(
                "test-key", "https://example.invalid", "gpt-4o-mini", "", objectMapper);
    }

    @Test
    void parseCategoryMapsEtcFromAiResponse() {
        assertThat(classifier.parseCategory(chatCompletion("{\"category\":\"ETC\"}"), "수분 선크림"))
                .isEqualTo(ProductCategory.ETC);
    }

    @Test
    void parseCategoryMapsNineStepCategory() {
        assertThat(classifier.parseCategory(chatCompletion("{\"category\":\"CREAM\"}"), "수분 크림"))
                .isEqualTo(ProductCategory.CREAM);
    }

    @Test
    void parseCategoryReturnsNullWhenContentIsBlankOrInvalid() {
        assertThat(classifier.parseCategory(chatCompletion(" "), "수분 크림")).isNull();
        assertThat(classifier.parseCategory(chatCompletion("{\"category\":\"NOT_A_CATEGORY\"}"), "수분 크림"))
                .isNull();
        assertThat(classifier.parseCategory(null, "수분 크림")).isNull();
    }

    private ObjectNode chatCompletion(String content) {
        ObjectNode response = objectMapper.createObjectNode();
        response.putArray("choices").addObject()
                .putObject("message")
                .put("content", content);
        return response;
    }
}
