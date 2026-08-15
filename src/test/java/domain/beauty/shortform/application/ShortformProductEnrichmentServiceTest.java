package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import domain.beauty.domain.BeautyRoutineAnalysis.EvidenceSource;
import domain.beauty.domain.BeautyRoutineAnalysis.IdentificationLevel;
import domain.beauty.domain.BeautyRoutineAnalysis.PurposeBasis;
import domain.beauty.domain.BeautyRoutineAnalysis.Step;
import domain.beauty.shortform.client.OpenAiProductEnrichmentClient;
import domain.beauty.shortform.client.ProductEnrichmentInput;
import domain.beauty.shortform.client.ProductEnrichmentResult;
import domain.beauty.shortform.config.OpenAiRoutineProperties;
import domain.beauty.shortform.domain.ShortformProductEnrichment;
import domain.beauty.shortform.domain.ShortformProductEnrichmentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class ShortformProductEnrichmentServiceTest {

    @Test
    void batchesCacheMissesAndReusesStoredProductsWithoutOpenAiTokens() {
        ShortformProductEnrichmentRepository repository = mock(ShortformProductEnrichmentRepository.class);
        OpenAiProductEnrichmentClient client = mock(OpenAiProductEnrichmentClient.class);
        OpenAiRoutineProperties properties = new OpenAiRoutineProperties();
        ShortformAnalysisJsonMapper jsonMapper = new ShortformAnalysisJsonMapper(new ObjectMapper());
        ShortformProductEnrichmentService service = new ShortformProductEnrichmentService(
                repository, client, properties, jsonMapper);
        List<ShortformProductEnrichment> stored = new ArrayList<>();
        AtomicInteger lookup = new AtomicInteger();
        when(repository.findByCacheKeyIn(any())).thenAnswer(invocation ->
                lookup.getAndIncrement() == 0 ? List.of() : List.copyOf(stored));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            ShortformProductEnrichment entity = invocation.getArgument(0);
            stored.add(entity);
            return entity;
        });
        when(client.enrich(any())).thenAnswer(invocation -> responseFor(invocation.getArgument(0)));

        List<Step> steps = List.of(exactStep(1, "1025 Dokdo Toner"), exactStep(2, "Birch Juice Ampoule"));
        ShortformProductEnrichmentService.BatchResult first = service.getOrEnrich(steps);
        ShortformProductEnrichmentService.BatchResult cached = service.getOrEnrich(steps);

        ArgumentCaptor<ProductEnrichmentInput> request = ArgumentCaptor.forClass(ProductEnrichmentInput.class);
        verify(client, times(1)).enrich(request.capture());
        assertThat(request.getValue().products()).hasSize(2);
        assertThat(first.cacheMisses()).isEqualTo(2);
        assertThat(first.outputTokens()).isPositive();
        assertThat(cached.cacheHits()).isEqualTo(2);
        assertThat(cached.cacheMisses()).isZero();
        assertThat(cached.inputTokens()).isZero();
        assertThat(cached.outputTokens()).isZero();
        assertThat(cached.productsByOrder().get(1).ingredients()).hasSize(1);
    }

    @Test
    void repairsConfidentProductWithEmptyIngredientsOnlyOnce() {
        ShortformProductEnrichmentRepository repository = mock(ShortformProductEnrichmentRepository.class);
        OpenAiProductEnrichmentClient client = mock(OpenAiProductEnrichmentClient.class);
        OpenAiRoutineProperties properties = new OpenAiRoutineProperties();
        ShortformProductEnrichmentService service = new ShortformProductEnrichmentService(
                repository, client, properties,
                new ShortformAnalysisJsonMapper(new ObjectMapper()));
        when(repository.findByCacheKeyIn(any())).thenReturn(List.of());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AtomicInteger calls = new AtomicInteger();
        when(client.enrich(any())).thenAnswer(invocation -> {
            ProductEnrichmentInput input = invocation.getArgument(0);
            String key = input.products().getFirst().requestKey();
            List<ProductEnrichmentResult.Ingredient> ingredients = calls.getAndIncrement() == 0
                    ? List.of()
                    : List.of(ingredient());
            return new ProductEnrichmentResult.Response(
                    new ProductEnrichmentResult(List.of(new ProductEnrichmentResult.Product(
                            key, "라운드랩", "1025 독도 토너", 0.96, ingredients))),
                    "gpt-test", 20, 10);
        });

        ShortformProductEnrichmentService.BatchResult result = service.getOrEnrich(
                List.of(exactStep(1, "1025 Dokdo Toner")));

        verify(client, times(2)).enrich(any());
        assertThat(result.productsByOrder().get(1).ingredients()).hasSize(1);
        assertThat(result.inputTokens()).isEqualTo(40);
    }

    private ProductEnrichmentResult.Response responseFor(ProductEnrichmentInput input) {
        List<ProductEnrichmentResult.Product> products = input.products().stream()
                .map(item -> new ProductEnrichmentResult.Product(
                        item.requestKey(), "라운드랩", item.rawProductName(), 0.95, List.of(ingredient())))
                .toList();
        return new ProductEnrichmentResult.Response(
                new ProductEnrichmentResult(products), "gpt-test", 30, 20);
    }

    private ProductEnrichmentResult.Ingredient ingredient() {
        return new ProductEnrichmentResult.Ingredient(
                1, "정제수", List.of("용제"), List.of("피부 보습"), 1, false, false);
    }

    private Step exactStep(int order, String name) {
        return new Step(
                order, "00:0" + order, null, "얼굴", "도포", "흡수", "보습",
                PurposeBasis.DIRECTLY_STATED, null, IdentificationLevel.EXACT_PRODUCT,
                "스킨케어", "ROUND LAB", name, null, "ROUND LAB " + name, null,
                List.of(EvidenceSource.VISUAL_LABEL), "용기 라벨에서 확인", 0.95);
    }
}
