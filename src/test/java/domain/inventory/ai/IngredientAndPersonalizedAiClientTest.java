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
    void fallsBackToGeminiWhenOpenAiIngredientLookupFails() {
        IngredientAiClient client = new IngredientAiClient(openAiIngredientClient, geminiJsonClient);
        when(openAiIngredientClient.fetchIngredientNames("바닥 토너"))
                .thenThrow(new AiProviderUnavailableException("429"));
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.putArray("ingredients").add("정제수").add("글리세린");
        when(geminiJsonClient.generateJson(eq(OpenAiIngredientClient.SYSTEM_PROMPT), eq("제품명: 바닥 토너")))
                .thenReturn(payload);

        assertThat(client.fetchIngredientNames("바닥 토너")).containsExactly("정제수", "글리세린");
        verify(geminiJsonClient).generateJson(any(), any());
    }

    @Test
    void returnsEmptyWhenBothIngredientProvidersFail() {
        IngredientAiClient client = new IngredientAiClient(openAiIngredientClient, geminiJsonClient);
        when(openAiIngredientClient.fetchIngredientNames("바닥 토너"))
                .thenThrow(new AiProviderUnavailableException("429"));
        when(geminiJsonClient.generateJson(any(), any()))
                .thenThrow(new AiProviderUnavailableException("gemini down"));

        assertThat(client.fetchIngredientNames("바닥 토너")).isEmpty();
    }

    @Test
    void skipsGeminiWhenOpenAiIngredientLookupSucceeds() {
        IngredientAiClient client = new IngredientAiClient(openAiIngredientClient, geminiJsonClient);
        when(openAiIngredientClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of("정제수"));

        assertThat(client.fetchIngredientNames("바닥 토너")).containsExactly("정제수");
        verify(geminiJsonClient, never()).generateJson(any(), any());
    }

    @Test
    void fallsBackToGeminiForPersonalizedAnalysis() {
        PersonalizedAnalysisAiClient client =
                new PersonalizedAnalysisAiClient(openAiPersonalizedAnalysisClient, geminiJsonClient);
        when(openAiPersonalizedAnalysisClient.analyze("바닥 토너", List.of("정제수"), SkinType.DRY, Set.of()))
                .thenThrow(new AiProviderUnavailableException("insufficient_quota"));
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("score", 72);
        payload.putArray("keywords").addObject().put("keyword", "보습").put("reason", "건성에 맞음");
        when(geminiJsonClient.generateJson(any(), any())).thenReturn(payload);

        PersonalizedAnalysisResult result =
                client.analyze("바닥 토너", List.of("정제수"), SkinType.DRY, Set.of());

        assertThat(result.score()).isEqualTo(72);
        assertThat(result.keywords()).extracting(PersonalizedAnalysisResult.Keyword::keyword).containsExactly("보습");
    }

    @Test
    void parsePurposesFromGeminiPayload() {
        IngredientAiClient client = new IngredientAiClient(openAiIngredientClient, geminiJsonClient);
        when(openAiIngredientClient.fetchIngredientPurposes(List.of("정제수")))
                .thenThrow(new AiProviderUnavailableException("timeout"));
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.putArray("ingredients").addObject()
                .put("name", "정제수")
                .putArray("purposes").add("기제(용매)");
        when(geminiJsonClient.generateJson(any(), any())).thenReturn(payload);

        Map<String, List<String>> purposes = client.fetchIngredientPurposes(List.of("정제수"));
        assertThat(purposes.get("정제수")).containsExactly("기제(용매)");
    }
}
