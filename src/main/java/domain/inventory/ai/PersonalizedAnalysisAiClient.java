package domain.inventory.ai;

import domain.inventory.client.OpenAiPersonalizedAnalysisClient;
import domain.inventory.client.PersonalizedAnalysisResult;
import domain.member.SkinConcern;
import domain.member.SkinType;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Slf4j
@Component
public class PersonalizedAnalysisAiClient {

    private final InventoryGeminiJsonClient geminiJsonClient;

    public PersonalizedAnalysisAiClient(InventoryGeminiJsonClient geminiJsonClient) {
        this.geminiJsonClient = geminiJsonClient;
    }

    public PersonalizedAnalysisResult analyze(
            String productName, List<String> ingredientNames, SkinType skinType, Set<SkinConcern> skinConcerns) {
        String userPrompt = OpenAiPersonalizedAnalysisClient.buildUserContent(
                productName, ingredientNames, skinType, skinConcerns);
        try {
            JsonNode payload = geminiJsonClient.generateJson(
                    OpenAiPersonalizedAnalysisClient.SYSTEM_PROMPT, userPrompt);
            PersonalizedAnalysisResult result = OpenAiPersonalizedAnalysisClient.parseResult(payload);
            if (result == null) {
                throw new AiProviderUnavailableException("Gemini 맞춤 분석 응답이 비어 있습니다.");
            }
            return result;
        } catch (AiProviderUnavailableException e) {
            log.warn("맞춤 분석 Gemini 실패: productName={}, message={}", productName, e.getMessage());
            return null;
        }
    }
}
