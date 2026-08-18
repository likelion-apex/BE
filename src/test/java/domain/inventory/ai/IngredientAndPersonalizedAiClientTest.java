package domain.inventory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import domain.cosmetic.client.OpenAiIngredientClient;
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
    private OpenAiPersonalizedAnalysisClient openAiPersonalizedAnalysisClient;

    @Test
    void usesGeminiWithoutCallingOpenAiWhenGeminiSucceeds() {
        IngredientAiClient client = client();
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.putArray("ingredients").add("정제수").add("글리세린");
        when(geminiJsonClient.generateJson(eq(OpenAiIngredientClient.SYSTEM_PROMPT), eq("제품명: 바닥 토너")))
                .thenReturn(payload);

        assertThat(client.fetchIngredientNames("바닥 토너")).containsExactly("정제수", "글리세린");
        verify(openAiIngredientClient, never()).fetchIngredientNames(any());
    }

    @Test
    void fallsBackToOpenAiWhenGeminiIngredientLookupFails() {
        IngredientAiClient client = client();
        when(geminiJsonClient.generateJson(any(), any()))
                .thenThrow(new AiProviderUnavailableException("gemini down"));
        when(openAiIngredientClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of("정제수", "글리세린"));

        assertThat(client.fetchIngredientNames("바닥 토너")).containsExactly("정제수", "글리세린");
    }

    @Test
    void returnsEmptyWhenBothIngredientProvidersFail() {
        IngredientAiClient client = client();
        when(geminiJsonClient.generateJson(any(), any()))
                .thenThrow(new AiProviderUnavailableException("gemini down"));
        when(openAiIngredientClient.fetchIngredientNames("바닥 토너"))
                .thenThrow(new AiProviderUnavailableException("429"));

        assertThat(client.fetchIngredientNames("바닥 토너")).isEmpty();
    }

    @Test
    void skipsOpenAiAfterCooldownWhenGeminiFails() {
        OpenAiSkipGate skipGate = new OpenAiSkipGate();
        skipGate.markUnavailable();
        IngredientAiClient client = new IngredientAiClient(
                openAiIngredientClient, geminiJsonClient, skipGate, new InventoryAiProperties());
        when(geminiJsonClient.generateJson(any(), any()))
                .thenThrow(new AiProviderUnavailableException("gemini down"));

        assertThat(client.fetchIngredientNames("바닥 토너")).isEmpty();
        verify(openAiIngredientClient, never()).fetchIngredientNames(any());
    }

    @Test
    void skipsOpenAiFallbackWhenEstimatedInputTokensExceedBudget() {
        InventoryAiProperties properties = new InventoryAiProperties();
        properties.setOpenaiMaxInputTokens(1);
        IngredientAiClient client = new IngredientAiClient(
                openAiIngredientClient, geminiJsonClient, new OpenAiSkipGate(), properties);
        when(geminiJsonClient.generateJson(any(), any()))
                .thenThrow(new AiProviderUnavailableException("gemini down"));

        assertThat(client.fetchIngredientNames("바닥 토너")).isEmpty();
        verify(openAiIngredientClient, never()).fetchIngredientNames(any());
    }

    @Test
    void usesGeminiForPersonalizedAnalysisWithoutOpenAi() {
        PersonalizedAnalysisAiClient client = personalizedClient();
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("score", 72);
        payload.putArray("keywords").addObject().put("keyword", "보습").put("reason", "건성에 맞음");
        when(geminiJsonClient.generateJson(any(), any())).thenReturn(payload);

        PersonalizedAnalysisResult result =
                client.analyze("바닥 토너", List.of("정제수"), SkinType.DRY, Set.of());

        assertThat(result.score()).isEqualTo(72);
        assertThat(result.keywords()).extracting(PersonalizedAnalysisResult.Keyword::keyword).containsExactly("보습");
        verify(openAiPersonalizedAnalysisClient, never()).analyze(any(), any(), any(), any());
    }

    @Test
    void fallsBackToOpenAiForPersonalizedAnalysis() {
        PersonalizedAnalysisAiClient client = personalizedClient();
        when(geminiJsonClient.generateJson(any(), any()))
                .thenThrow(new AiProviderUnavailableException("gemini down"));
        when(openAiPersonalizedAnalysisClient.analyze("바닥 토너", List.of("정제수"), SkinType.DRY, Set.of()))
                .thenReturn(new PersonalizedAnalysisResult(
                        80, List.of(new PersonalizedAnalysisResult.Keyword("보습", "건성에 맞음"))));

        PersonalizedAnalysisResult result =
                client.analyze("바닥 토너", List.of("정제수"), SkinType.DRY, Set.of());

        assertThat(result.score()).isEqualTo(80);
    }

    @Test
    void returnsNullWhenBothPersonalizedProvidersFail() {
        PersonalizedAnalysisAiClient client = personalizedClient();
        when(geminiJsonClient.generateJson(any(), any()))
                .thenThrow(new AiProviderUnavailableException("gemini down"));
        when(openAiPersonalizedAnalysisClient.analyze("바닥 토너", List.of("정제수"), SkinType.DRY, Set.of()))
                .thenThrow(AiProviderUnavailableException.quota("insufficient_quota", null));

        assertThat(client.analyze("바닥 토너", List.of("정제수"), SkinType.DRY, Set.of())).isNull();
    }

    @Test
    void parsePurposesFromGeminiPayload() {
        IngredientAiClient client = client();
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.putArray("ingredients").addObject()
                .put("name", "정제수")
                .putArray("purposes").add("기제(용매)");
        when(geminiJsonClient.generateJson(any(), any())).thenReturn(payload);

        Map<String, List<String>> purposes = client.fetchIngredientPurposes(List.of("정제수"));
        assertThat(purposes.get("정제수")).containsExactly("기제(용매)");
        verify(openAiIngredientClient, never()).fetchIngredientPurposes(any());
    }

    private IngredientAiClient client() {
        return new IngredientAiClient(
                openAiIngredientClient, geminiJsonClient, new OpenAiSkipGate(), new InventoryAiProperties());
    }

    private PersonalizedAnalysisAiClient personalizedClient() {
        return new PersonalizedAnalysisAiClient(
                openAiPersonalizedAnalysisClient,
                geminiJsonClient,
                new OpenAiSkipGate(),
                new InventoryAiProperties());
    }
}
