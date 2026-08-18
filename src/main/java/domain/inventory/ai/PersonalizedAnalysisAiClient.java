package domain.inventory.ai;

import domain.inventory.client.GroqPersonalizedAnalysisClient;
import domain.inventory.client.OpenAiPersonalizedAnalysisClient;
import domain.inventory.client.PersonalizedAnalysisResult;
import domain.member.SkinConcern;
import domain.member.SkinType;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 맞춤 분석을 캐시 → OpenAI(1차) → Gemini(폴백1) → Groq(폴백2) 순서로 시도한다.
 * 각 단계는 실제 예외가 발생했을 때만 다음 provider로 넘어가며, 사전 토큰 추정은 사용하지 않는다.
 */
@Slf4j
@Component
public class PersonalizedAnalysisAiClient {

    private static final List<AiProvider> ORDER = List.of(AiProvider.OPENAI, AiProvider.GEMINI, AiProvider.GROQ);

    private final OpenAiPersonalizedAnalysisClient openAiPersonalizedAnalysisClient;
    private final InventoryGeminiJsonClient geminiJsonClient;
    private final GroqPersonalizedAnalysisClient groqPersonalizedAnalysisClient;
    private final AiProviderSkipGate skipGate;

    public PersonalizedAnalysisAiClient(
            OpenAiPersonalizedAnalysisClient openAiPersonalizedAnalysisClient,
            InventoryGeminiJsonClient geminiJsonClient,
            GroqPersonalizedAnalysisClient groqPersonalizedAnalysisClient,
            AiProviderSkipGate skipGate) {
        this.openAiPersonalizedAnalysisClient = openAiPersonalizedAnalysisClient;
        this.geminiJsonClient = geminiJsonClient;
        this.groqPersonalizedAnalysisClient = groqPersonalizedAnalysisClient;
        this.skipGate = skipGate;
    }

    public PersonalizedAnalysisResult analyze(
            String productName, List<String> ingredientNames, SkinType skinType, Set<SkinConcern> skinConcerns) {
        if (productName == null || productName.isBlank()) {
            return null;
        }
        String userPrompt = OpenAiPersonalizedAnalysisClient.buildUserContent(
                productName, ingredientNames, skinType, skinConcerns);
        for (AiProvider provider : ORDER) {
            if (skipGate.shouldSkip(provider)) {
                continue;
            }
            try {
                PersonalizedAnalysisResult result = switch (provider) {
                    case OPENAI -> openAiPersonalizedAnalysisClient.analyze(
                            productName, ingredientNames, skinType, skinConcerns);
                    case GEMINI -> geminiResult(userPrompt);
                    case GROQ -> groqPersonalizedAnalysisClient.analyze(
                            productName, ingredientNames, skinType, skinConcerns);
                };
                if (result != null) {
                    return result;
                }
            } catch (AiProviderUnavailableException e) {
                log.warn("맞춤 분석 {} 실패: productName={}, message={}", provider, productName, e.getMessage());
                skipGate.markFrom(provider, e);
            }
        }
        return null;
    }

    private PersonalizedAnalysisResult geminiResult(String userPrompt) {
        JsonNode payload = geminiJsonClient.generateJson(OpenAiPersonalizedAnalysisClient.SYSTEM_PROMPT, userPrompt);
        PersonalizedAnalysisResult result = OpenAiPersonalizedAnalysisClient.parseResult(payload);
        if (result == null) {
            throw new AiProviderUnavailableException("Gemini 맞춤 분석 응답이 비어 있습니다.");
        }
        return result;
    }
}
