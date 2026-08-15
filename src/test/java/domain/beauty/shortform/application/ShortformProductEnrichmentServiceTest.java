package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
import domain.beauty.shortform.client.ProductEnrichmentResult.LookupStatus;
import domain.beauty.shortform.config.OpenAiRoutineProperties;
import domain.beauty.shortform.domain.IngredientSourceType;
import domain.beauty.shortform.domain.IngredientVerificationStatus;
import domain.beauty.shortform.domain.ShortformProductEnrichment;
import domain.beauty.shortform.domain.ShortformProductEnrichmentRepository;
import global.exception.CustomException;
import global.exception.ErrorCode;
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
    void usesFallbackModelOnceWhenPrimaryCannotVerifyIngredients() {
        ShortformProductEnrichmentRepository repository = mock(ShortformProductEnrichmentRepository.class);
        OpenAiProductEnrichmentClient client = mock(OpenAiProductEnrichmentClient.class);
        OpenAiRoutineProperties properties = new OpenAiRoutineProperties();
        ShortformProductEnrichmentService service = new ShortformProductEnrichmentService(
                repository, client, properties,
                new ShortformAnalysisJsonMapper(new ObjectMapper()));
        when(repository.findByCacheKeyIn(any())).thenReturn(List.of());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.enrich(any())).thenAnswer(invocation -> {
            ProductEnrichmentInput input = invocation.getArgument(0);
            String key = input.products().getFirst().requestKey();
            return new ProductEnrichmentResult.Response(
                    new ProductEnrichmentResult(List.of(product(key, List.of(), List.of()))),
                    "gpt-test", 20, 10);
        });
        when(client.enrich(any(), anyString(), anyInt())).thenAnswer(invocation -> {
            ProductEnrichmentInput input = invocation.getArgument(0);
            String key = input.products().getFirst().requestKey();
            return response(key, "gpt-fallback", List.of(ingredient()));
        });

        ShortformProductEnrichmentService.BatchResult result = service.getOrEnrich(
                List.of(exactStep(1, "1025 Dokdo Toner")));

        verify(client, times(1)).enrich(any());
        verify(client, times(1)).enrich(any(), anyString(), anyInt());
        assertThat(result.productsByOrder().get(1).ingredients()).hasSize(1);
        assertThat(result.inputTokens()).isEqualTo(40);
        assertThat(result.model()).contains("gpt-test", "gpt-fallback");
    }

    @Test
    void rejectsIngredientSourceThatWasNotReturnedByWebSearch() {
        ShortformProductEnrichmentRepository repository = mock(ShortformProductEnrichmentRepository.class);
        OpenAiProductEnrichmentClient client = mock(OpenAiProductEnrichmentClient.class);
        OpenAiRoutineProperties properties = new OpenAiRoutineProperties();
        properties.setProductFallbackEnabled(false);
        ShortformProductEnrichmentService service = new ShortformProductEnrichmentService(
                repository, client, properties,
                new ShortformAnalysisJsonMapper(new ObjectMapper()));
        when(repository.findByCacheKeyIn(any())).thenReturn(List.of());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.enrich(any())).thenAnswer(invocation -> {
            ProductEnrichmentInput input = invocation.getArgument(0);
            String key = input.products().getFirst().requestKey();
            return new ProductEnrichmentResult.Response(
                    new ProductEnrichmentResult(List.of(product(
                            key, List.of(ingredient()), List.of(source())))),
                    "gpt-test",
                    20,
                    10,
                    1,
                    List.of(new ProductEnrichmentResult.WebSource(
                            "https://unrelated.example/products/other", "다른 제품"))
            );
        });

        ProductEnrichmentData result = service.getOrEnrich(
                List.of(exactStep(1, "1025 Dokdo Toner"))).productsByOrder().get(1);

        assertThat(result.ingredients()).isEmpty();
        assertThat(result.sources()).isEmpty();
        assertThat(result.ingredientVerificationStatus())
                .isEqualTo(IngredientVerificationStatus.UNVERIFIED);
    }

    @Test
    void completesWithPrimaryResultWhenOptionalFallbackIsRateLimited() {
        ShortformProductEnrichmentRepository repository = mock(ShortformProductEnrichmentRepository.class);
        OpenAiProductEnrichmentClient client = mock(OpenAiProductEnrichmentClient.class);
        OpenAiRoutineProperties properties = new OpenAiRoutineProperties();
        ShortformProductEnrichmentService service = new ShortformProductEnrichmentService(
                repository, client, properties,
                new ShortformAnalysisJsonMapper(new ObjectMapper()));
        when(repository.findByCacheKeyIn(any())).thenReturn(List.of());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.enrich(any())).thenAnswer(invocation -> {
            ProductEnrichmentInput input = invocation.getArgument(0);
            String key = input.products().getFirst().requestKey();
            return new ProductEnrichmentResult.Response(
                    new ProductEnrichmentResult(List.of(product(key, List.of(), List.of()))),
                    "gpt-test", 20, 10);
        });
        when(client.enrich(any(), anyString(), anyInt())).thenThrow(new CustomException(
                ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE));

        ShortformProductEnrichmentService.BatchResult result = service.getOrEnrich(
                List.of(exactStep(1, "1025 Dokdo Toner")));

        assertThat(result.productsByOrder().get(1).ingredients()).isEmpty();
        assertThat(result.model()).isEqualTo("gpt-test");
        verify(repository).saveAndFlush(any());
    }

    @Test
    void usesFallbackModelForWholeBatchWhenPrimaryModelIsUnavailable() {
        ShortformProductEnrichmentRepository repository = mock(ShortformProductEnrichmentRepository.class);
        OpenAiProductEnrichmentClient client = mock(OpenAiProductEnrichmentClient.class);
        OpenAiRoutineProperties properties = new OpenAiRoutineProperties();
        ShortformProductEnrichmentService service = new ShortformProductEnrichmentService(
                repository, client, properties,
                new ShortformAnalysisJsonMapper(new ObjectMapper()));
        when(repository.findByCacheKeyIn(any())).thenReturn(List.of());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.enrich(any())).thenThrow(new CustomException(
                ErrorCode.SHORTFORM_EXTERNAL_API_UNAVAILABLE));
        when(client.enrich(any(), anyString())).thenAnswer(invocation -> {
            ProductEnrichmentInput input = invocation.getArgument(0);
            return response(
                    input.products().getFirst().requestKey(),
                    "gpt-fallback",
                    List.of(ingredient()));
        });

        ShortformProductEnrichmentService.BatchResult result = service.getOrEnrich(
                List.of(exactStep(1, "1025 Dokdo Toner")));

        assertThat(result.productsByOrder().get(1).ingredients()).hasSize(1);
        assertThat(result.model()).isEqualTo("gpt-fallback");
        verify(client).enrich(any(), anyString());
        verify(client, times(0)).enrich(any(), anyString(), anyInt());
    }

    private ProductEnrichmentResult.Response responseFor(ProductEnrichmentInput input) {
        List<ProductEnrichmentResult.Product> products = input.products().stream()
                .map(item -> product(item.requestKey(), List.of(ingredient()), List.of(source())))
                .toList();
        return new ProductEnrichmentResult.Response(
                new ProductEnrichmentResult(products), "gpt-test", 30, 20, 1, List.of(webSource()));
    }

    private ProductEnrichmentResult.Response response(
            String key,
            String model,
            List<ProductEnrichmentResult.Ingredient> ingredients
    ) {
        return new ProductEnrichmentResult.Response(
                new ProductEnrichmentResult(List.of(product(key, ingredients, List.of(source())))),
                model,
                20,
                10,
                1,
                List.of(webSource())
        );
    }

    private ProductEnrichmentResult.Product product(
            String key,
            List<ProductEnrichmentResult.Ingredient> ingredients,
            List<ProductEnrichmentResult.Source> sources
    ) {
        return new ProductEnrichmentResult.Product(
                key,
                "라운드랩",
                "1025 독도 토너",
                "한국 판매 처방",
                LookupStatus.FOUND,
                0.95,
                "공식 페이지에서 확인",
                sources,
                ingredients
        );
    }

    private ProductEnrichmentResult.Source source() {
        return new ProductEnrichmentResult.Source(
                "https://roundlab.com/products/1025-dokdo-toner",
                "1025 Dokdo Toner",
                IngredientSourceType.OFFICIAL
        );
    }

    private ProductEnrichmentResult.WebSource webSource() {
        return new ProductEnrichmentResult.WebSource(
                "https://roundlab.com/products/1025-dokdo-toner",
                "1025 Dokdo Toner"
        );
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
