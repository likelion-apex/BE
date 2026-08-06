package feat.apex_BE.beauty.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GeminiPromptResources {

	public static final String VERSION = "1.0";

	private final String systemPrompt;
	private final String userPrompt;
	private final JsonNode responseSchema;

	public GeminiPromptResources(ObjectMapper objectMapper) {
		try {
			this.systemPrompt = readText("gemini/beauty-routine-system-prompt.txt");
			this.userPrompt = readText("gemini/beauty-routine-user-prompt.txt");
			this.responseSchema = objectMapper.readTree(
				new ClassPathResource("gemini/beauty-routine-schema.json").getInputStream()
			);
		} catch (IOException exception) {
			throw new IllegalStateException("Gemini 프롬프트 리소스를 읽을 수 없습니다.", exception);
		}
	}

	public String systemPrompt() {
		return systemPrompt;
	}

	public String userPrompt() {
		return userPrompt;
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
