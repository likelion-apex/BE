package domain.beauty.shortform.client;

import static org.assertj.core.api.Assertions.assertThat;

import domain.beauty.shortform.config.OpenAiRoutineProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OpenAiRoutinePromptResourcesTest {

    @Test
    void usesOpenAiCompatibleArraySchemaAndKeepsDuplicatePreventionInstruction() {
        OpenAiRoutinePromptResources resources = new OpenAiRoutinePromptResources(
                new ObjectMapper(), new OpenAiRoutineProperties());

        JsonNode reasons = resources.responseSchema()
                .path("properties")
                .path("steps")
                .path("items")
                .path("properties")
                .path("reasons");

        assertThat(reasons.path("minItems").asInt()).isEqualTo(2);
        assertThat(reasons.path("maxItems").asInt()).isEqualTo(4);
        assertThat(reasons.has("uniqueItems")).isFalse();
        assertThat(resources.systemPrompt())
                .contains("같은 assessmentCategory를 중복하지 마세요");
    }
}
