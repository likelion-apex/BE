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

    private final OpenAiPersonalizedAnalysisClient openAiPersonalizedAnalysisClient;
    private final InventoryGeminiJsonClient geminiJsonClient;
    private final OpenAiSkipGate openAiSkipGate;
    private final InventoryAiProperties inventoryAiProperties;

    public PersonalizedAnalysisAiClient(
            OpenAiPersonalizedAnalysisClient openAiPersonalizedAnalysisClient,
            InventoryGeminiJsonClient geminiJsonClient,
            OpenAiSkipGate openAiSkipGate,
            InventoryAiProperties inventoryAiProperties) {
        this.openAiPersonalizedAnalysisClient = openAiPersonalizedAnalysisClient;
        this.geminiJsonClient = geminiJsonClient;
        this.openAiSkipGate = openAiSkipGate;
        this.inventoryAiProperties = inventoryAiProperties;
    }

    public PersonalizedAnalysisResult analyze(
            String productName, List<String> ingredientNames, SkinType skinType, Set<SkinConcern> skinConcerns) {
        String userPrompt = OpenAiPersonalizedAnalysisClient.buildUserContent(
                productName, ingredientNames, skinType, skinConcerns);
        if (shouldCallOpenAi(OpenAiPersonalizedAnalysisClient.SYSTEM_PROMPT, userPrompt)) {
            try {
                return openAiPersonalizedAnalysisClient.analyze(
                        productName, ingredientNames, skinType, skinConcerns);
            } catch (AiProviderUnavailableException e) {
                openAiSkipGate.markUnavailable();
                log.warn("맞춤 분석 OpenAI 실패, Gemini로 폴백: productName={}, message={}", productName, e.getMessage());
            }
        }
        try {
            JsonNode payload = geminiJsonClient.generateJson(
                    OpenAiPersonalizedAnalysisClient.SYSTEM_PROMPT, userPrompt);
            PersonalizedAnalysisResult result = OpenAiPersonalizedAnalysisClient.parseResult(payload);
            if (result == null) {
                throw new AiProviderUnavailableException("Gemini 맞춤 분석 응답이 비어 있습니다.");
            }
            return result;
        } catch (AiProviderUnavailableException fallback) {
            log.warn("맞춤 분석 Gemini 폴백 실패: productName={}, message={}", productName, fallback.getMessage());
            return null;
        }
    }

    private boolean shouldCallOpenAi(String... promptParts) {
        if (openAiSkipGate.shouldSkip()) {
            return false;
        }
        int estimated = InventoryAiTokenEstimator.estimate(promptParts);
        int limit = inventoryAiProperties.getOpenaiMaxInputTokens();
        if (estimated > limit) {
            log.info("예상 입력 토큰 {}이 한도 {}를 넘어 OpenAI를 건너뛰고 Gemini를 사용합니다.", estimated, limit);
            return false;
        }
        return true;
    }
}
