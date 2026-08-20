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
    void openAiReturnsEmptyList_thenGeminiFallbackSucceedsWithoutCooldown() {
        // 화장품은 항상 전성분이 있어야 하므로, 예외 없이 빈 배열이 와도 실패로 간주해
        // 다음 provider로 넘어간다. 단, 이건 provider 장애가 아니라 콘텐츠 문제이므로
        // 쿨다운(markFrom)은 걸지 않는다.
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GEMINI)).thenReturn(false);
        when(openAiIngredientClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of());
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.putArray("ingredients").add("정제수");
        when(geminiJsonClient.generateJson(eq(OpenAiIngredientClient.SYSTEM_PROMPT), anyString()))
                .thenReturn(payload);

        List<String> result = ingredientClient().fetchIngredientNames("바닥 토너");

        assertThat(result).containsExactly("정제수");
        verify(skipGate, never()).markFrom(eq(AiProvider.OPENAI), any());
    }

    @Test
    void allThreeProvidersReturnEmptyList_returnsEmptyListWithoutCooldown() {
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GEMINI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GROQ)).thenReturn(false);
        ObjectNode emptyPayload = new ObjectMapper().createObjectNode();
        emptyPayload.putArray("ingredients");
        when(openAiIngredientClient.fetchIngredientNames(any())).thenReturn(List.of());
        when(geminiJsonClient.generateJson(any(), any())).thenReturn(emptyPayload);
        when(groqIngredientClient.fetchIngredientNames(any())).thenReturn(List.of());

        assertThat(ingredientClient().fetchIngredientNames("바닥 토너")).isEmpty();
        verify(skipGate, never()).markFrom(any(), any());
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

    @Test
    void details_openAiReturnsEmptyMap_thenGeminiFallbackSucceeds() {
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GEMINI)).thenReturn(false);
        when(openAiIngredientClient.fetchIngredientDetails(List.of("정제수"))).thenReturn(Map.of());
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.putArray("ingredients").addObject()
                .put("name", "정제수").putArray("purposes").add("용제");
        when(geminiJsonClient.generateJson(eq(OpenAiIngredientClient.DETAIL_SYSTEM_PROMPT), anyString()))
                .thenReturn(payload);

        Map<String, IngredientAiDetail> result = ingredientClient().fetchIngredientDetails(List.of("정제수"));

        assertThat(result.get("정제수").purposes()).containsExactly("용제");
        verify(skipGate, never()).markFrom(eq(AiProvider.OPENAI), any());
    }

    @Test
    void details_moreThanEightIngredients_splitsIntoBatchesAndMergesResults() {
        List<String> tenIngredients = List.of(
                "성분1", "성분2", "성분3", "성분4", "성분5", "성분6", "성분7", "성분8", "성분9", "성분10");
        List<String> firstBatch = tenIngredients.subList(0, 8);
        List<String> secondBatch = tenIngredients.subList(8, 10);

        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GEMINI)).thenReturn(false);
        Map<String, IngredientAiDetail> firstBatchResult = firstBatch.stream()
                .collect(java.util.stream.Collectors.toMap(
                        name -> name, name -> new IngredientAiDetail(List.of("용제"), List.of(), "LOW")));
        when(openAiIngredientClient.fetchIngredientDetails(firstBatch)).thenReturn(firstBatchResult);
        when(openAiIngredientClient.fetchIngredientDetails(secondBatch))
                .thenThrow(new AiProviderUnavailableException("openai down"));
        ObjectNode secondBatchPayload = new ObjectMapper().createObjectNode();
        var ingredientsArray = secondBatchPayload.putArray("ingredients");
        ingredientsArray.addObject().put("name", "성분9").putArray("purposes").add("보습제");
        ingredientsArray.addObject().put("name", "성분10").putArray("purposes").add("보습제");
        when(geminiJsonClient.generateJson(eq(OpenAiIngredientClient.DETAIL_SYSTEM_PROMPT), anyString()))
                .thenReturn(secondBatchPayload);

        Map<String, IngredientAiDetail> result = ingredientClient().fetchIngredientDetails(tenIngredients);

        assertThat(result).hasSize(10);
        assertThat(result.get("성분1").purposes()).containsExactly("용제");
        assertThat(result.get("성분9").purposes()).containsExactly("보습제");
        assertThat(result.get("성분10").purposes()).containsExactly("보습제");
        verify(openAiIngredientClient).fetchIngredientDetails(firstBatch);
        verify(openAiIngredientClient).fetchIngredientDetails(secondBatch);
        verify(skipGate).markFrom(eq(AiProvider.OPENAI), any());
    }

    @Test
    void details_exactlyEightIngredients_doesNotSplitIntoBatches() {
        List<String> eightIngredients = List.of(
                "성분1", "성분2", "성분3", "성분4", "성분5", "성분6", "성분7", "성분8");
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        Map<String, IngredientAiDetail> result = eightIngredients.stream()
                .collect(java.util.stream.Collectors.toMap(
                        name -> name, name -> new IngredientAiDetail(List.of("용제"), List.of(), "LOW")));
        when(openAiIngredientClient.fetchIngredientDetails(eightIngredients)).thenReturn(result);

        Map<String, IngredientAiDetail> actual = ingredientClient().fetchIngredientDetails(eightIngredients);

        assertThat(actual).hasSize(8);
        verify(openAiIngredientClient, org.mockito.Mockito.times(1)).fetchIngredientDetails(any());
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
        var keywordsArray = payload.putArray("keywords");
        keywordsArray.addObject().put("keyword", "보습").put("reason", "건성에 맞음");
        keywordsArray.addObject().put("keyword", "저자극").put("reason", "민감성 성분 없음");
        keywordsArray.addObject().put("keyword", "veganCertified").put("reason", "비건 인증 성분");
        when(geminiJsonClient.generateJson(eq(OpenAiPersonalizedAnalysisClient.SYSTEM_PROMPT), anyString()))
                .thenReturn(payload);

        PersonalizedAnalysisResult result =
                personalizedClient().analyze("바닥 토너", List.of("정제수"), SkinType.DRY, Set.of());

        assertThat(result.score()).isEqualTo(65);
        assertThat(result.keywords()).hasSize(3);
        verify(skipGate).markFrom(eq(AiProvider.OPENAI), any());
        verify(groqPersonalizedAnalysisClient, never()).analyze(any(), any(), any(), any());
    }

    @Test
    void analyze_geminiReturnsIncompleteKeywords_isAcceptedWithoutGroqFallback() {
        // keywords가 부족해도 score만 유효하면 그 provider의 결과를 그대로 채택한다.
        // 부족한 keywords는 InventoryService가 기본값으로 채우므로, 여기서 Groq까지
        // 추가로 호출하지 않는다(불필요한 지연시간·네트워크 실패 유발을 방지).
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GEMINI)).thenReturn(false);
        when(openAiPersonalizedAnalysisClient.analyze(any(), any(), any(), any()))
                .thenThrow(new AiProviderUnavailableException("openai down"));
        ObjectNode incompletePayload = new ObjectMapper().createObjectNode();
        incompletePayload.put("score", 65);
        incompletePayload.putArray("keywords").addObject().put("keyword", "보습").put("reason", "건성에 맞음");
        when(geminiJsonClient.generateJson(eq(OpenAiPersonalizedAnalysisClient.SYSTEM_PROMPT), anyString()))
                .thenReturn(incompletePayload);

        PersonalizedAnalysisResult result =
                personalizedClient().analyze("바닥 토너", List.of("정제수"), SkinType.DRY, Set.of());

        assertThat(result.score()).isEqualTo(65);
        assertThat(result.keywords()).hasSize(1);
        verify(skipGate).markFrom(eq(AiProvider.OPENAI), any());
        verify(groqPersonalizedAnalysisClient, never()).analyze(any(), any(), any(), any());
    }

    @Test
    void analyze_geminiReturnsZeroValidKeywords_thenGroqFallbackSucceeds() {
        // keyword가 단 하나도 유효하지 않은(완전히 빈) 응답은 provider가 사실상 응답을
        // 만들어내지 못한 것으로 보고 다음 provider로 폴백한다.
        when(skipGate.shouldSkip(AiProvider.OPENAI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GEMINI)).thenReturn(false);
        when(skipGate.shouldSkip(AiProvider.GROQ)).thenReturn(false);
        when(openAiPersonalizedAnalysisClient.analyze(any(), any(), any(), any()))
                .thenThrow(new AiProviderUnavailableException("openai down"));
        ObjectNode emptyKeywordsPayload = new ObjectMapper().createObjectNode();
        emptyKeywordsPayload.put("score", 65);
        emptyKeywordsPayload.putArray("keywords");
        when(geminiJsonClient.generateJson(eq(OpenAiPersonalizedAnalysisClient.SYSTEM_PROMPT), anyString()))
                .thenReturn(emptyKeywordsPayload);
        PersonalizedAnalysisResult groqResult = new PersonalizedAnalysisResult(
                58, List.of(new PersonalizedAnalysisResult.Keyword("보습", "건성에 맞음")));
        when(groqPersonalizedAnalysisClient.analyze(eq("바닥 토너"), any(), any(), any()))
                .thenReturn(groqResult);

        PersonalizedAnalysisResult result =
                personalizedClient().analyze("바닥 토너", List.of("정제수"), SkinType.DRY, Set.of());

        assertThat(result.score()).isEqualTo(58);
        verify(skipGate).markFrom(eq(AiProvider.OPENAI), any());
        verify(skipGate, never()).markFrom(eq(AiProvider.GEMINI), any());
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
