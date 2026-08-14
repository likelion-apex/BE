package domain.beauty.shortform.client;

import domain.beauty.shortform.config.OpenAiRoutineProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenAiRoutinePromptResources {

    private final String systemPrompt;
    private final JsonNode responseSchema;
    private final OpenAiRoutineProperties properties;

    public OpenAiRoutinePromptResources(ObjectMapper objectMapper, OpenAiRoutineProperties properties) {
        this.properties = properties;
        try {
            this.systemPrompt = new ClassPathResource("openai/shortform-routine-system-prompt.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
            this.responseSchema = objectMapper.readTree(
                    new ClassPathResource("openai/shortform-routine-schema.json").getInputStream()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("OpenAI 루틴 분석 프롬프트를 읽을 수 없습니다.", exception);
        }
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public JsonNode responseSchema() {
        return responseSchema;
    }

    public String version() {
        return properties.getRoutinePromptVersion();
    }
}
