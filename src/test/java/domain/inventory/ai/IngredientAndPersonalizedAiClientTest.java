package domain.inventory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import domain.cosmetic.client.GroqIngredientClient;
import domain.cosmetic.client.OpenAiIngredientClient;
import domain.inventory.client.GroqPersonalizedAnalysisClient;
import domain.inventory.client.OpenAiPersonalizedAnalysisClient;
import domain.inventory.client.PersonalizedAnalysisResult;
import domain.member.SkinType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class IngredientAndPersonalizedAiClientTest {

    @Mock
    private OpenAiIngredientClient openAiIngredientClient;

    @Mock
    private InventoryGeminiJsonClient geminiJsonClient;

    @Mock
    private GroqIngredientClient groqIngredientClient;

    @Mock
    private OpenAiPersonalizedAnalysisClient openAiPersonalizedAnalysisClient;

    @Mock
    private GroqPersonalizedAnalysisClient groqPersonalizedAnalysisClient;

    @Mock
    private AiProviderSkipGate skipGate;

    private IngredientAiClient ingredientClient() {
        return new IngredientAiClient(openAiIngredientClient, geminiJsonClient, groqIngredientClient, skipGate);
    }

    private PersonalizedAnalysisAiClient personalizedClient() {
        return new PersonalizedAnalysisAiClient(
                openAiPersonalizedAnalysisClient, geminiJsonClient, groqPersonalizedAnalysisClient, skipGate);
    }

    // ---- fetchIngredientNames ----

    @Test
    void openAiSucceeds_thenGeminiAndGroqAreNotCalled() {
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(openAiIngredientClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of("정제수", "글리세린"));

        List<String> result = ingredientClient().fetchIngredientNames("바닥 토너");

        assertThat(result).containsExactly("정제수", "글리세린");
        verify(geminiJsonClient, never()).generateJson(any(), any());
        verify(groqIngredientClient, never()).fetchIngredientNames(any());
    }

    @Test
    void openAiFails_thenGeminiFallbackSucceedsAndMarksOpenAiCooldown() {
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GEMINI)).thenReturn(false);
        when(openAiIngredientClient.fetchIngredientNames("바닥 토너"))
                .thenThrow(AiProviderUnavailableException.quota("429", null));
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.putArray("ingredients").add("정제수");
        when(geminiJsonClient.generateJson(eq(OpenAiIngredientClient.SYSTEM_PROMPT), anyString()))
                .thenReturn(payload);

        List<String> result = ingredientClient().fetchIngredientNames("바닥 토너");

        assertThat(result).containsExactly("정제수");
        verify(skipGate).markFrom(eq(AiProvider.OPENAI), any(AiProviderUnavailableException.class));
        verify(groqIngredientClient, never()).fetchIngredientNames(any());
    }

    @Test
    void openAiAndGeminiFail_thenGroqFallbackSucceeds() {
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GEMINI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GROQ)).thenReturn(false);
        when(openAiIngredientClient.fetchIngredientNames("바닥 토너"))
                .thenThrow(new AiProviderUnavailableException("openai down"));
        when(geminiJsonClient.generateJson(any(), any()))
                .thenThrow(new AiProviderUnavailableException("gemini down"));
        when(groqIngredientClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of("정제수"));

        List<String> result = ingredientClient().fetchIngredientNames("바닥 토너");

        assertThat(result).containsExactly("정제수");
        verify(skipGate).markFrom(eq(AiProvider.OPENAI), any());
        verify(skipGate).markFrom(eq(AiProvider.GEMINI), any());
    }

    @Test
    void openAiInCooldown_isSkippedImmediatelyAndGeminiIsUsed() {
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(true);
        when(skipGate.shouldSkip(AiProvider.GEMINI)).thenReturn(false);
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.putArray("ingredients").add("정제수");
        when(geminiJsonClient.generateJson(any(), any())).thenReturn(payload);

        List<String> result = ingredientClient().fetchIngredientNames("바닥 토너");

        assertThat(result).containsExactly("정제수");
        verify(openAiIngredientClient, never()).fetchIngredientNames(any());
        verify(skipGate, never()).markFrom(eq(AiProvider.OPENAI), any());
    }

    @Test
    void allThreeProvidersFail_returnsEmptyList() {
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GEMINI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GROQ)).thenReturn(false);
        when(openAiIngredientClient.fetchIngredientNames(any()))
                .thenThrow(new AiProviderUnavailableException("openai down"));
        when(geminiJsonClient.generateJson(any(), any()))
                .thenThrow(new AiProviderUnavailableException("gemini down"));
        when(groqIngredientClient.fetchIngredientNames(any()))
                .thenThrow(new AiProviderUnavailableException("groq down"));

        assertThat(ingredientClient().fetchIngredientNames("바닥 토너")).isEmpty();
    }

    @Test
    void allProvidersInCooldown_returnsEmptyListWithoutCallingAny() {
        when(skipGate.shouldSkip(any())).thenReturn(true);

        assertThat(ingredientClient().fetchIngredientNames("바닥 토너")).isEmpty();
        verify(openAiIngredientClient, never()).fetchIngredientNames(any());
        verify(geminiJsonClient, never()).generateJson(any(), any());
        verify(groqIngredientClient, never()).fetchIngredientNames(any());
    }

    // ---- fetchIngredientDetails ----

    @Test
    void details_openAiSucceeds_thenGeminiAndGroqAreNotCalled() {
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(openAiIngredientClient.fetchIngredientDetails(List.of("정제수")))
                .thenReturn(Map.of("정제수", new IngredientAiDetail(List.of("용제"), List.of("피부 보습"), "LOW")));

        Map<String, IngredientAiDetail> result = ingredientClient().fetchIngredientDetails(List.of("정제수"));

        assertThat(result.get("정제수").purposes()).containsExactly("용제");
        assertThat(result.get("정제수").efficacyTags()).containsExactly("피부 보습");
        assertThat(result.get("정제수").riskLevel()).isEqualTo("LOW");
        verify(geminiJsonClient, never()).generateJson(any(), any());
        verify(groqIngredientClient, never()).fetchIngredientDetails(any());
    }

    @Test
    void details_openAiFails_thenGroqUsedAfterGeminiAlsoFails() {
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GEMINI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GROQ)).thenReturn(false);
        when(openAiIngredientClient.fetchIngredientDetails(any()))
                .thenThrow(new AiProviderUnavailableException("openai down"));
        when(geminiJsonClient.generateJson(eq(OpenAiIngredientClient.DETAIL_SYSTEM_PROMPT), anyString()))
                .thenThrow(new AiProviderUnavailableException("gemini down"));
        when(groqIngredientClient.fetchIngredientDetails(List.of("정제수")))
                .thenReturn(Map.of("정제수", new IngredientAiDetail(List.of("피부컨디셔닝제"), List.of(), "MEDIUM")));

        Map<String, IngredientAiDetail> result = ingredientClient().fetchIngredientDetails(List.of("정제수"));

        assertThat(result.get("정제수").purposes()).containsExactly("피부컨디셔닝제");
        assertThat(result.get("정제수").riskLevel()).isEqualTo("MEDIUM");
    }

    // ---- PersonalizedAnalysisAiClient.analyze ----

    @Test
    void analyze_openAiSucceeds_thenGeminiAndGroqAreNotCalled() {
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        PersonalizedAnalysisResult openAiResult = new PersonalizedAnalysisResult(72, List.of());
        when(openAiPersonalizedAnalysisClient.analyze(eq("바닥 토너"), any(), any(), any()))
                .thenReturn(openAiResult);

        PersonalizedAnalysisResult result =
                personalizedClient().analyze("바닥 토너", List.of("정제수"), SkinType.DRY, Set.of());

        assertThat(result.score()).isEqualTo(72);
        verify(geminiJsonClient, never()).generateJson(any(), any());
        verify(groqPersonalizedAnalysisClient, never()).analyze(any(), any(), any(), any());
    }

    @Test
    void analyze_openAiFails_thenGeminiFallbackSucceeds() {
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GEMINI)).thenReturn(false);
        when(openAiPersonalizedAnalysisClient.analyze(any(), any(), any(), any()))
                .thenThrow(new AiProviderUnavailableException("openai down"));
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("score", 65);
        payload.putArray("keywords").addObject().put("keyword", "보습").put("reason", "건성에 맞음");
        when(geminiJsonClient.generateJson(eq(OpenAiPersonalizedAnalysisClient.SYSTEM_PROMPT), anyString()))
                .thenReturn(payload);

        PersonalizedAnalysisResult result =
                personalizedClient().analyze("바닥 토너", List.of("정제수"), SkinType.DRY, Set.of());

        assertThat(result.score()).isEqualTo(65);
        verify(skipGate).markFrom(eq(AiProvider.OPENAI), any());
        verify(groqPersonalizedAnalysisClient, never()).analyze(any(), any(), any(), any());
    }

    @Test
    void analyze_openAiAndGeminiFail_thenGroqFallbackSucceeds() {
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GEMINI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GROQ)).thenReturn(false);
        when(openAiPersonalizedAnalysisClient.analyze(any(), any(), any(), any()))
                .thenThrow(new AiProviderUnavailableException("openai down"));
        when(geminiJsonClient.generateJson(any(), any()))
                .thenThrow(new AiProviderUnavailableException("gemini down"));
        PersonalizedAnalysisResult groqResult = new PersonalizedAnalysisResult(58, List.of());
        when(groqPersonalizedAnalysisClient.analyze(eq("바닥 토너"), any(), any(), any()))
                .thenReturn(groqResult);

        PersonalizedAnalysisResult result =
                personalizedClient().analyze("바닥 토너", List.of("정제수"), SkinType.DRY, Set.of());

        assertThat(result.score()).isEqualTo(58);
        verify(skipGate).markFrom(eq(AiProvider.OPENAI), any());
        verify(skipGate).markFrom(eq(AiProvider.GEMINI), any());
    }

    @Test
    void analyze_geminiInCooldown_skipsDirectlyToGroq() {
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GEMINI)).thenReturn(true);
        when(skipGate.shouldSkip(AiProvider.GROQ)).thenReturn(false);
        when(openAiPersonalizedAnalysisClient.analyze(any(), any(), any(), any()))
                .thenThrow(new AiProviderUnavailableException("openai down"));
        PersonalizedAnalysisResult groqResult = new PersonalizedAnalysisResult(58, List.of());
        when(groqPersonalizedAnalysisClient.analyze(eq("바닥 토너"), any(), any(), any()))
                .thenReturn(groqResult);

        PersonalizedAnalysisResult result =
                personalizedClient().analyze("바닥 토너", List.of("정제수"), SkinType.DRY, Set.of());

        assertThat(result.score()).isEqualTo(58);
        verify(geminiJsonClient, never()).generateJson(any(), any());
        verify(skipGate, never()).markFrom(eq(AiProvider.GEMINI), any());
    }

    @Test
    void analyze_allThreeFail_returnsNull() {
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GEMINI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GROQ)).thenReturn(false);
        when(openAiPersonalizedAnalysisClient.analyze(any(), any(), any(), any()))
                .thenThrow(new AiProviderUnavailableException("openai down"));
        when(geminiJsonClient.generateJson(any(), any()))
                .thenThrow(new AiProviderUnavailableException("gemini down"));
        when(groqPersonalizedAnalysisClient.analyze(any(), any(), any(), any()))
                .thenThrow(new AiProviderUnavailableException("groq down"));

        assertThat(personalizedClient().analyze("바닥 토너", List.of("정제수"), SkinType.DRY, Set.of())).isNull();
    }

    // ---- inferBrand ----

    @Test
    void brand_openAiSucceeds_thenGeminiAndGroqAreNotCalled() {
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(openAiIngredientClient.fetchBrand("바닥 토너")).thenReturn("이니스프리");

        String result = ingredientClient().inferBrand("바닥 토너");

        assertThat(result).isEqualTo("이니스프리");
        verify(geminiJsonClient, never()).generateJson(any(), any());
        verify(groqIngredientClient, never()).fetchBrand(any());
    }

    @Test
    void brand_openAiFails_thenGeminiFallbackSucceeds() {
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GEMINI)).thenReturn(false);
        when(openAiIngredientClient.fetchBrand(any()))
                .thenThrow(new AiProviderUnavailableException("openai down"));
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("brand", "이니스프리");
        when(geminiJsonClient.generateJson(eq(OpenAiIngredientClient.BRAND_SYSTEM_PROMPT), anyString()))
                .thenReturn(payload);

        String result = ingredientClient().inferBrand("바닥 토너");

        assertThat(result).isEqualTo("이니스프리");
        verify(skipGate).markFrom(eq(AiProvider.OPENAI), any());
        verify(groqIngredientClient, never()).fetchBrand(any());
    }

    @Test
    void brand_openAiAndGeminiFail_thenGroqFallbackSucceeds() {
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GEMINI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GROQ)).thenReturn(false);
        when(openAiIngredientClient.fetchBrand(any()))
                .thenThrow(new AiProviderUnavailableException("openai down"));
        when(geminiJsonClient.generateJson(any(), any()))
                .thenThrow(new AiProviderUnavailableException("gemini down"));
        when(groqIngredientClient.fetchBrand("바닥 토너")).thenReturn("이니스프리");

        String result = ingredientClient().inferBrand("바닥 토너");

        assertThat(result).isEqualTo("이니스프리");
        verify(skipGate).markFrom(eq(AiProvider.OPENAI), any());
        verify(skipGate).markFrom(eq(AiProvider.GEMINI), any());
    }
}
