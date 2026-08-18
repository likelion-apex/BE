package domain.beauty.shortform.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenAiOptimizationReasonPromptResources {

    private final String systemPrompt;
    private final JsonNode responseSchema;

    public OpenAiOptimizationReasonPromptResources(ObjectMapper objectMapper) {
        try {
            this.systemPrompt = new ClassPathResource("openai/shortform-optimization-reason-system-prompt.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
            this.responseSchema = objectMapper.readTree(
                    new ClassPathResource("openai/shortform-optimization-reason-schema.json").getInputStream());
        } catch (IOException exception) {
            throw new IllegalStateException("OpenAI 최적화 이유 프롬프트를 읽을 수 없습니다.", exception);
        }
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public JsonNode responseSchema() {
        return responseSchema;
    }
}
