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
                log.info("맞춤 분석 {} 쿨다운 중이라 건너뜁니다: productName={}", provider, productName);
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
                    log.info("맞춤 분석 성공: provider={}, productName={}, keywordCount={}",
                            provider, productName, result.keywords() == null ? 0 : result.keywords().size());
                    return result;
                }
                // 완전히 빈 응답(유효 keyword 0개)은 콘텐츠 품질 문제이지 provider 장애가
                // 아니므로, 쿨다운 없이 이 요청 안에서만 다음 provider로 넘어간다.
                log.warn("맞춤 분석 {} 응답이 비어 있어 다음 provider로 넘어갑니다: productName={}", provider, productName);
            } catch (AiProviderUnavailableException e) {
                if (e.isQuotaExceeded()) {
                    log.warn("맞춤 분석 {} 할당량 소진으로 실패, 쿨다운을 겁니다: productName={}, retryAfter={}, message={}",
                            provider, productName, e.getRetryAfter(), e.getMessage());
                } else {
                    log.warn("맞춤 분석 {} 실패(쿨다운 없음): productName={}, message={}", provider, productName, e.getMessage());
                }
                skipGate.markFrom(provider, e);
            }
        }
        log.warn("맞춤 분석이 모든 provider에서 비어 있거나 실패했습니다: productName={}", productName);
        return null;
    }

    private PersonalizedAnalysisResult geminiResult(String userPrompt) {
        JsonNode payload = geminiJsonClient.generateJson(OpenAiPersonalizedAnalysisClient.SYSTEM_PROMPT, userPrompt);
        // null(완전히 빈 응답)은 예외로 바꾸지 않고 그대로 반환한다 - 호출부가 쿨다운 없이
        // 다음 provider로 넘어가도록 한다.
        return OpenAiPersonalizedAnalysisClient.parseResult(payload);
    }
}
