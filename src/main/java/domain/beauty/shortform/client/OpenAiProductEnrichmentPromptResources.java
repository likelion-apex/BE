package domain.beauty.shortform.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenAiProductEnrichmentPromptResources {

    private final String systemPrompt;
    private final JsonNode responseSchema;

    public OpenAiProductEnrichmentPromptResources(ObjectMapper objectMapper) {
        try {
            systemPrompt = readText("openai/shortform-product-enrichment-system-prompt.txt");
            responseSchema = objectMapper.readTree(new ClassPathResource(
                    "openai/shortform-product-enrichment-schema.json").getInputStream());
        } catch (IOException exception) {
            throw new IllegalStateException("OpenAI 제품 보강 프롬프트 리소스를 읽을 수 없습니다.", exception);
        }
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public JsonNode responseSchema() {
        return responseSchema;
    }

    private String readText(String path) throws IOException {
        try (var inputStream = new ClassPathResource(path).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }
}
