package domain.inventory.ai;

import domain.cosmetic.client.GroqIngredientClient;
import domain.cosmetic.client.OpenAiIngredientClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 전성분/배합목적 조회를 캐시 → OpenAI(1차) → Gemini(폴백1) → Groq(폴백2) 순서로 시도한다.
 * 각 단계는 실제 예외가 발생했을 때만 다음 provider로 넘어가며, 사전 토큰 추정은 사용하지 않는다.
 */
@Slf4j
@Component
public class IngredientAiClient {

    private static final List<AiProvider> ORDER = List.of(AiProvider.OPENAI, AiProvider.GEMINI, AiProvider.GROQ);
    private static final int DETAIL_BATCH_SIZE = 8;

    private final OpenAiIngredientClient openAiIngredientClient;
    private final InventoryGeminiJsonClient geminiJsonClient;
    private final GroqIngredientClient groqIngredientClient;
    private final AiProviderSkipGate skipGate;

    public IngredientAiClient(
            OpenAiIngredientClient openAiIngredientClient,
            InventoryGeminiJsonClient geminiJsonClient,
            GroqIngredientClient groqIngredientClient,
            AiProviderSkipGate skipGate) {
        this.openAiIngredientClient = openAiIngredientClient;
        this.geminiJsonClient = geminiJsonClient;
        this.groqIngredientClient = groqIngredientClient;
        this.skipGate = skipGate;
    }

    public List<String> fetchIngredientNames(String productName) {
        if (productName == null || productName.isBlank()) {
            return List.of();
        }
        String userPrompt = "제품명: " + productName;
        for (AiProvider provider : ORDER) {
            if (skipGate.shouldSkip(provider)) {
                log.info("전성분 조회 {} 쿨다운 중이라 건너뜁니다: productName={}", provider, productName);
                continue;
            }
            try {
                List<String> ingredients = switch (provider) {
                    case OPENAI -> openAiIngredientClient.fetchIngredientNames(productName);
                    case GEMINI -> InventoryAiJsonSupport.parseIngredientNames(
                            geminiJsonClient.generateJson(OpenAiIngredientClient.SYSTEM_PROMPT, userPrompt));
                    case GROQ -> groqIngredientClient.fetchIngredientNames(productName);
                };
                // 화장품은 항상 전성분이 있어야 하므로, 예외 없이 빈 배열이 와도 정상 성공으로
                // 받아들이지 않고 다음 provider로 넘어간다. 콘텐츠 품질 문제이지 provider
                // 장애가 아니므로 쿨다운은 걸지 않는다(이 요청 안에서만 다음 provider 시도).
                if (!ingredients.isEmpty()) {
                    log.info("전성분 조회 성공: provider={}, productName={}, count={}",
                            provider, productName, ingredients.size());
                    return ingredients;
                }
                log.warn("전성분 {} 응답이 비어 있어 다음 provider로 넘어갑니다: productName={}", provider, productName);
            } catch (AiProviderUnavailableException e) {
                logFailure("전성분", provider, productName, e);
                skipGate.markFrom(provider, e);
            }
        }
        log.warn("전성분 조회가 모든 provider에서 비어 있거나 실패했습니다: productName={}", productName);
        return List.of();
    }

    public Map<String, IngredientAiDetail> fetchIngredientDetails(List<String> ingredientNames) {
        if (ingredientNames == null || ingredientNames.isEmpty()) {
            return Map.of();
        }
        if (ingredientNames.size() <= DETAIL_BATCH_SIZE) {
            return fetchIngredientDetailsBatch(ingredientNames);
        }
        // 성분 목록이 길면 단일 요청이 타임아웃되기 쉬우므로, 배치로 나눠 각 배치를 독립적으로
        // 폴백시키고 결과를 병합한다. 한 배치가 실패해도 다른 배치 결과는 유지된다.
        Map<String, IngredientAiDetail> merged = new LinkedHashMap<>();
        for (List<String> batch : partition(ingredientNames, DETAIL_BATCH_SIZE)) {
            merged.putAll(fetchIngredientDetailsBatch(batch));
        }
        return merged;
    }

    private Map<String, IngredientAiDetail> fetchIngredientDetailsBatch(List<String> ingredientNames) {
        String userPrompt = "성분 목록: " + String.join(", ", ingredientNames);
        for (AiProvider provider : ORDER) {
            if (skipGate.shouldSkip(provider)) {
                log.info("배합목적/위험도 조회 {} 쿨다운 중이라 건너뜁니다: batchSize={}", provider, ingredientNames.size());
                continue;
            }
            try {
                Map<String, IngredientAiDetail> details = switch (provider) {
                    case OPENAI -> openAiIngredientClient.fetchIngredientDetails(ingredientNames);
                    case GEMINI -> InventoryAiJsonSupport.parseIngredientDetails(geminiJsonClient.generateJson(
                            OpenAiIngredientClient.DETAIL_SYSTEM_PROMPT, userPrompt));
                    case GROQ -> groqIngredientClient.fetchIngredientDetails(ingredientNames);
                };
                if (!details.isEmpty()) {
                    log.info("배합목적/위험도 조회 성공: provider={}, count={}", provider, details.size());
                    return details;
                }
                log.warn("배합목적/위험도 {} 응답이 비어 있어 다음 provider로 넘어갑니다", provider);
            } catch (AiProviderUnavailableException e) {
                logFailure("배합목적/위험도", provider, null, e);
                skipGate.markFrom(provider, e);
            }
        }
        log.warn("배합목적/위험도 조회가 모든 provider에서 비어 있거나 실패했습니다: batchSize={}", ingredientNames.size());
        return Map.of();
    }

    private static List<List<String>> partition(List<String> items, int size) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += size) {
            batches.add(items.subList(i, Math.min(i + size, items.size())));
        }
        return batches;
    }

    public String inferBrand(String productName) {
        if (productName == null || productName.isBlank()) {
            return null;
        }
        String userPrompt = "제품명: " + productName;
        for (AiProvider provider : ORDER) {
            if (skipGate.shouldSkip(provider)) {
                log.info("브랜드 추론 {} 쿨다운 중이라 건너뜁니다: productName={}", provider, productName);
                continue;
            }
            try {
                return switch (provider) {
                    case OPENAI -> openAiIngredientClient.fetchBrand(productName);
                    case GEMINI -> InventoryAiJsonSupport.parseBrand(
                            geminiJsonClient.generateJson(OpenAiIngredientClient.BRAND_SYSTEM_PROMPT, userPrompt));
                    case GROQ -> groqIngredientClient.fetchBrand(productName);
                };
            } catch (AiProviderUnavailableException e) {
                logFailure("브랜드 추론", provider, productName, e);
                skipGate.markFrom(provider, e);
            }
        }
        return null;
    }

    private static void logFailure(String label, AiProvider provider, String productName, AiProviderUnavailableException e) {
        if (e.isQuotaExceeded()) {
            log.warn("{} {} 할당량 소진으로 실패, 쿨다운을 겁니다: productName={}, retryAfter={}, message={}",
                    label, provider, productName, e.getRetryAfter(), e.getMessage());
        } else {
            log.warn("{} {} 실패(쿨다운 없음): productName={}, message={}", label, provider, productName, e.getMessage());
        }
    }
}
