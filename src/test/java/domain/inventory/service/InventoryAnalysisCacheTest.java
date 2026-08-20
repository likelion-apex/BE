package domain.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import domain.beauty.shortform.application.ProductCapacityNormalizer;
import domain.inventory.Inventory;
import domain.inventory.InventoryRepository;
import domain.inventory.Product;
import domain.inventory.ProductCategory;
import domain.inventory.ai.CautionIngredientCatalog;
import domain.inventory.ai.IngredientAiClient;
import domain.inventory.ai.IngredientAiDetail;
import domain.inventory.ai.InventoryAiCacheService;
import domain.inventory.ai.PersonalizedAnalysisAiClient;
import domain.inventory.client.PersonalizedAnalysisResult;
import domain.inventory.dto.response.AiAnalysisResponse;
import domain.inventory.dto.response.IngredientAnalysisResponse;
import domain.inventory.dto.response.IngredientPurposeCategory;
import domain.inventory.dto.response.IngredientRiskLevel;
import domain.inventory.dto.response.SkinEfficacyTag;
import domain.member.Member;
import domain.member.MemberRepository;
import domain.member.Provider;
import domain.member.Role;
import domain.member.SkinType;
import global.util.PublicUrlResolver;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class InventoryAnalysisCacheTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ProductService productService;
    @Mock
    private IngredientAiClient ingredientAiClient;
    @Mock
    private PersonalizedAnalysisAiClient personalizedAnalysisAiClient;
    @Mock
    private InventoryAiCacheService inventoryAiCacheService;

    private InventoryService inventoryService;
    private Member member;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(
                inventoryRepository,
                memberRepository,
                productService,
                ingredientAiClient,
                personalizedAnalysisAiClient,
                inventoryAiCacheService,
                new PublicUrlResolver(""),
                new ProductCapacityNormalizer(),
                new CautionIngredientCatalog());
        member = Member.builder()
                .nickname("테스터")
                .provider(Provider.KAKAO)
                .providerId("inventory-cache-test")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(member, "id", 9L);
        ReflectionTestUtils.setField(member, "skinType", SkinType.DRY);
        Product product = Product.builder()
                .name("바닥 토너")
                .brand("바닥 브랜드")
                .category(ProductCategory.SKIN_TONER)
                .build();
        ReflectionTestUtils.setField(product, "id", 3L);
        inventory = Inventory.builder().member(member).product(product).build();
        ReflectionTestUtils.setField(inventory, "id", 11L);
    }

    @Test
    void ingredientAnalysisUsesCacheAndSkipsAi() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.putArray("ingredients").addObject()
                .put("ingredientName", "정제수")
                .putArray("purposes").add("용제");
        when(inventoryAiCacheService.find(InventoryAiCacheService.ingredientKey("바닥 토너")))
                .thenReturn(Optional.of(payload));

        IngredientAnalysisResponse response = inventoryService.getIngredientAnalysis(9L, 11L);

        assertThat(response.ingredients()).containsExactly(
                new IngredientAnalysisResponse.IngredientDetail(
                        "정제수", List.of(IngredientPurposeCategory.SOLVENT), List.of(),
                        IngredientRiskLevel.MEDIUM));
        verify(ingredientAiClient, never()).fetchIngredientNames(any());
        verify(ingredientAiClient, never()).fetchIngredientDetails(any());
        verify(ingredientAiClient, never()).inferBrand(any());
    }

    @Test
    void personalizedAnalysisUsesCacheAndSkipsAi() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode ingredientPayload = mapper.createObjectNode();
        var ingredientsArray = ingredientPayload.putArray("ingredients");
        ingredientsArray.addObject().put("ingredientName", "정제수").put("riskLevel", "LOW")
                .putArray("purposes").add("용제");
        ingredientsArray.addObject().put("ingredientName", "글리세린").put("riskLevel", "LOW")
                .putArray("purposes").add("피부컨디셔닝제");
        ingredientsArray.addObject().put("ingredientName", "메틸파라벤").put("riskLevel", "MEDIUM")
                .putArray("purposes").add("변성제");
        when(inventoryAiCacheService.find(InventoryAiCacheService.ingredientKey("바닥 토너")))
                .thenReturn(Optional.of(ingredientPayload));

        ObjectNode keywordPayload = mapper.createObjectNode();
        var keywordsArray = keywordPayload.putArray("keywords");
        keywordsArray.addObject().put("keyword", "보습").put("reason", "건성");
        keywordsArray.addObject().put("keyword", "저자극").put("reason", "민감성 성분 없음");
        keywordsArray.addObject().put("keyword", "저알러지").put("reason", "알레르기 유발 성분 없음");
        when(inventoryAiCacheService.find(InventoryAiCacheService.personalizedKey(
                "바닥 토너", SkinType.DRY, Set.of())))
                .thenReturn(Optional.of(keywordPayload));

        AiAnalysisResponse response = inventoryService.getAiAnalysis(9L, 11L);

        assertThat(response.score()).isEqualTo(87);
        assertThat(response.keywords()).containsExactly(
                new AiAnalysisResponse.AnalysisKeyword("보습", "건성"),
                new AiAnalysisResponse.AnalysisKeyword("저자극", "민감성 성분 없음"),
                new AiAnalysisResponse.AnalysisKeyword("저알러지", "알레르기 유발 성분 없음"));
        verify(personalizedAnalysisAiClient, never()).analyze(any(), any(), any(), any());
        verify(ingredientAiClient, never()).fetchIngredientNames(any());
    }

    @Test
    void personalizedAnalysisTreatsIncompleteCachedKeywordsAsMissAndRetriesAi() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));
        when(inventoryAiCacheService.find(InventoryAiCacheService.ingredientKey("바닥 토너")))
                .thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of());

        ObjectNode incompleteKeywordPayload = new ObjectMapper().createObjectNode();
        incompleteKeywordPayload.putArray("keywords").addObject().put("keyword", "보습").put("reason", "건성");
        when(inventoryAiCacheService.find(InventoryAiCacheService.personalizedKey(
                "바닥 토너", SkinType.DRY, Set.of())))
                .thenReturn(Optional.of(incompleteKeywordPayload));
        when(personalizedAnalysisAiClient.analyze("바닥 토너", List.of(), SkinType.DRY, Set.of()))
                .thenReturn(new PersonalizedAnalysisResult(80, List.of(
                        new PersonalizedAnalysisResult.Keyword("보습", "건성"),
                        new PersonalizedAnalysisResult.Keyword("저자극", "민감성 성분 없음"),
                        new PersonalizedAnalysisResult.Keyword("저알러지", "알레르기 유발 성분 없음"))));

        AiAnalysisResponse response = inventoryService.getAiAnalysis(9L, 11L);

        assertThat(response.keywords()).hasSize(3);
        verify(personalizedAnalysisAiClient).analyze("바닥 토너", List.of(), SkinType.DRY, Set.of());
    }

    @Test
    void aiAnalysisIncludesResolvedImageUrl() {
        Product product = Product.builder()
                .name("바닥 토너")
                .brand("바닥 브랜드")
                .category(ProductCategory.SKIN_TONER)
                .imageUrl("/images/products/toner.png")
                .build();
        ReflectionTestUtils.setField(product, "id", 3L);
        Inventory imageInventory = Inventory.builder().member(member).product(product).build();
        ReflectionTestUtils.setField(imageInventory, "id", 11L);

        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(imageInventory));
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of());
        when(personalizedAnalysisAiClient.analyze("바닥 토너", List.of(), SkinType.DRY, Set.of()))
                .thenReturn(null);

        AiAnalysisResponse response = inventoryService.getAiAnalysis(9L, 11L);

        assertThat(response.imageUrl()).isEqualTo("/images/products/toner.png");
    }

    @Test
    void aiAnalysisIncludesFavoriteStatus() {
        inventory.updateFavorite(true);
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of());
        when(personalizedAnalysisAiClient.analyze("바닥 토너", List.of(), SkinType.DRY, Set.of()))
                .thenReturn(null);

        AiAnalysisResponse response = inventoryService.getAiAnalysis(9L, 11L);

        assertThat(response.isFavorite()).isTrue();
    }

    @Test
    void aiAnalysisScoreIsHighWhenIngredientsAreMostlyLowRisk() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너"))
                .thenReturn(List.of("정제수", "글리세린"));
        when(ingredientAiClient.fetchIngredientDetails(List.of("정제수", "글리세린")))
                .thenReturn(Map.of(
                        "정제수", new IngredientAiDetail(List.of("용제"), List.of(), "LOW"),
                        "글리세린", new IngredientAiDetail(List.of("피부컨디셔닝제"), List.of("피부 보습"), "LOW")));
        when(personalizedAnalysisAiClient.analyze(eq("바닥 토너"), any(), any(), any())).thenReturn(null);

        AiAnalysisResponse response = inventoryService.getAiAnalysis(9L, 11L);

        assertThat(response.score()).isEqualTo(100);
    }

    @Test
    void aiAnalysisScoreIsLowWhenIngredientsAreMostlyHighRisk() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너"))
                .thenReturn(List.of("리모넨", "트리클로산"));
        when(ingredientAiClient.fetchIngredientDetails(List.of("리모넨", "트리클로산")))
                .thenReturn(Map.of(
                        "리모넨", new IngredientAiDetail(List.of("향료"), List.of(), "HIGH"),
                        "트리클로산", new IngredientAiDetail(List.of("변성제"), List.of(), "HIGH")));
        when(personalizedAnalysisAiClient.analyze(eq("바닥 토너"), any(), any(), any())).thenReturn(null);

        AiAnalysisResponse response = inventoryService.getAiAnalysis(9L, 11L);

        assertThat(response.score()).isEqualTo(20);
    }

    @Test
    void ingredientAnalysisStoresCacheAfterAiHit() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of("정제수"));
        when(ingredientAiClient.fetchIngredientDetails(List.of("정제수")))
                .thenReturn(Map.of("정제수", new IngredientAiDetail(List.of("용제"), List.of(), "LOW")));

        inventoryService.getIngredientAnalysis(9L, 11L);

        verify(inventoryAiCacheService).save(eq(InventoryAiCacheService.ingredientKey("바닥 토너")), any());
    }

    @Test
    void doesNotCacheEmptyIngredientList() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of());

        inventoryService.getIngredientAnalysis(9L, 11L);

        verify(inventoryAiCacheService, never()).save(any(), any());
    }

    /**
     * 성분 이름은 3개 조회됐지만 상세(배합목적/위험도)는 일부만 확보된 경우(예: 배치 폴백 중
     * 일부 배치만 실패), 불완전한 결과를 캐시하면 30일 동안 나머지 성분이 기본값으로 고정되어
     * 버리므로 캐시하지 않아야 한다.
     */
    @Test
    void doesNotCacheIncompleteIngredientDetails() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너"))
                .thenReturn(List.of("정제수", "글리세린", "메틸파라벤"));
        when(ingredientAiClient.fetchIngredientDetails(List.of("정제수", "글리세린", "메틸파라벤")))
                .thenReturn(Map.of("정제수", new IngredientAiDetail(List.of("용제"), List.of(), "LOW")));

        IngredientAnalysisResponse response = inventoryService.getIngredientAnalysis(9L, 11L);

        assertThat(response.ingredients()).hasSize(3);
        verify(inventoryAiCacheService, never()).save(eq(InventoryAiCacheService.ingredientKey("바닥 토너")), any());
    }

    @Test
    void ingredientAnalysisAlwaysReturnsBrandCapacityAndCountsEvenWithoutIngredients() {
        Product product = Product.builder()
                .name("바닥 토너 200ml")
                .brand("바닥 브랜드")
                .category(ProductCategory.SKIN_TONER)
                .build();
        ReflectionTestUtils.setField(product, "id", 4L);
        Inventory emptyInventory = Inventory.builder().member(member).product(product).build();
        ReflectionTestUtils.setField(emptyInventory, "id", 12L);

        when(inventoryRepository.findByIdAndMemberId(12L, 9L)).thenReturn(Optional.of(emptyInventory));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너 200ml")).thenReturn(List.of());

        IngredientAnalysisResponse response = inventoryService.getIngredientAnalysis(9L, 12L);

        assertThat(response.brand()).isEqualTo("바닥 브랜드");
        assertThat(response.capacity()).isEqualTo("200ml");
        assertThat(response.ingredients()).isEmpty();
        assertThat(response.riskDistribution()).isEqualTo(new IngredientAnalysisResponse.RiskDistribution(0, 0, 0));
        assertThat(response.caution20Count()).isZero();
        assertThat(response.allergyCount()).isZero();
    }

    @Test
    void ingredientAnalysisCountsCaution20AndAllergyMatches() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너"))
                .thenReturn(List.of("메틸파라벤", "리모넨", "정제수"));
        when(ingredientAiClient.fetchIngredientDetails(List.of("메틸파라벤", "리모넨", "정제수")))
                .thenReturn(Map.of(
                        "메틸파라벤", new IngredientAiDetail(List.of("변성제"), List.of(), "MEDIUM"),
                        "리모넨", new IngredientAiDetail(List.of("향료"), List.of(), "HIGH"),
                        "정제수", new IngredientAiDetail(List.of("용제"), List.of(), "LOW")));

        IngredientAnalysisResponse response = inventoryService.getIngredientAnalysis(9L, 11L);

        assertThat(response.caution20Count()).isEqualTo(1);
        assertThat(response.allergyCount()).isEqualTo(1);
        assertThat(response.riskDistribution()).isEqualTo(new IngredientAnalysisResponse.RiskDistribution(1, 1, 1));
    }

    @Test
    void ingredientAnalysisStillReturnsWhenCacheFails() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(inventoryAiCacheService.find(any())).thenThrow(new RuntimeException("inventory_ai_caches missing"));
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of("정제수"));
        when(ingredientAiClient.fetchIngredientDetails(List.of("정제수")))
                .thenReturn(Map.of("정제수", new IngredientAiDetail(List.of("용제"), List.of(), "LOW")));
        doThrow(new RuntimeException("read-only transaction")).when(inventoryAiCacheService).save(any(), any());

        IngredientAnalysisResponse response = inventoryService.getIngredientAnalysis(9L, 11L);

        assertThat(response.ingredients()).containsExactly(
                new IngredientAnalysisResponse.IngredientDetail(
                        "정제수", List.of(IngredientPurposeCategory.SOLVENT), List.of(), IngredientRiskLevel.LOW));
    }

    /**
     * AI가 1~2개짜리 불완전한 keywords를 반환하면(정상 흐름에서는 provider 단계의 parseResult에서
     * 이미 걸러지지만, 방어 로직으로) InventoryService는 실제 AI 근거와 기본 문구가 섞이지 않도록
     * 전체를 기본 키워드 3개로 교체한다.
     */
    @Test
    void personalizedAnalysisStillReturnsWhenCacheFails() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));
        when(inventoryAiCacheService.find(any())).thenThrow(new RuntimeException("inventory_ai_caches missing"));
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of("정제수"));
        when(ingredientAiClient.fetchIngredientDetails(List.of("정제수")))
                .thenReturn(Map.of("정제수", new IngredientAiDetail(List.of("용제"), List.of(), "LOW")));
        when(personalizedAnalysisAiClient.analyze("바닥 토너", List.of("정제수"), SkinType.DRY, Set.of()))
                .thenReturn(new PersonalizedAnalysisResult(
                        80, List.of(new PersonalizedAnalysisResult.Keyword("보습", "건성"))));
        doThrow(new RuntimeException("read-only transaction")).when(inventoryAiCacheService).save(any(), any());

        AiAnalysisResponse response = inventoryService.getAiAnalysis(9L, 11L);

        assertThat(response.score()).isEqualTo(100);
        assertThat(response.keywords()).containsExactly(
                new AiAnalysisResponse.AnalysisKeyword("피부타입 적합도", "건성 기준으로 성분을 보수적으로 평가했습니다."),
                new AiAnalysisResponse.AnalysisKeyword("성분 안전성", "전성분 위험도 비율을 기준으로 적합 점수를 산출했습니다."),
                new AiAnalysisResponse.AnalysisKeyword("피부고민 맞춤", "특별한 피부고민이 없는 경우을 기준으로 적합도를 판단했습니다."));
    }

    @Test
    void personalizedAnalysisReturnsDefaultScoreAndDefaultKeywordsWhenAiReturnsNull() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of());
        when(personalizedAnalysisAiClient.analyze("바닥 토너", List.of(), SkinType.DRY, Set.of()))
                .thenReturn(null);

        AiAnalysisResponse response = inventoryService.getAiAnalysis(9L, 11L);

        assertThat(response.score()).isEqualTo(70);
        assertThat(response.keywords()).containsExactly(
                new AiAnalysisResponse.AnalysisKeyword("피부타입 적합도", "건성 기준으로 성분을 보수적으로 평가했습니다."),
                new AiAnalysisResponse.AnalysisKeyword(
                        "성분 안전성", "전성분 정보를 확인할 수 없어 제품명을 기준으로 보수적으로 평가했습니다."),
                new AiAnalysisResponse.AnalysisKeyword("피부고민 맞춤", "특별한 피부고민이 없는 경우을 기준으로 적합도를 판단했습니다."));
    }

    /**
     * AI가 완전히 실패(null)했을 때 빈/불완전 keywords를 캐시에 저장하면, 이후 AI가 복구되어도
     * TTL 동안 계속 부실한 결과만 반환하는 문제가 있었다. 실패·불완전은 캐시하지 않아야 하며,
     * 매 호출마다 AI를 다시 시도해서 복구 즉시 완전한 키워드가 채워지고 캐시되어야 한다.
     */
    @Test
    void doesNotCacheKeywordsWhenAiFailsAndRetriesOnNextCall() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of());
        when(personalizedAnalysisAiClient.analyze("바닥 토너", List.of(), SkinType.DRY, Set.of()))
                .thenReturn(null)
                .thenReturn(new PersonalizedAnalysisResult(80, List.of(
                        new PersonalizedAnalysisResult.Keyword("보습", "건성"),
                        new PersonalizedAnalysisResult.Keyword("저자극", "민감성 성분 없음"),
                        new PersonalizedAnalysisResult.Keyword("저알러지", "알레르기 유발 성분 없음"))));

        AiAnalysisResponse firstResponse = inventoryService.getAiAnalysis(9L, 11L);
        AiAnalysisResponse secondResponse = inventoryService.getAiAnalysis(9L, 11L);

        assertThat(firstResponse.keywords()).hasSize(3);
        assertThat(secondResponse.keywords()).containsExactly(
                new AiAnalysisResponse.AnalysisKeyword("보습", "건성"),
                new AiAnalysisResponse.AnalysisKeyword("저자극", "민감성 성분 없음"),
                new AiAnalysisResponse.AnalysisKeyword("저알러지", "알레르기 유발 성분 없음"));
        verify(personalizedAnalysisAiClient, times(2)).analyze(any(), any(), any(), any());
        verify(inventoryAiCacheService, never()).save(
                eq(InventoryAiCacheService.personalizedKey("바닥 토너", SkinType.DRY, Set.of())),
                argThat(payload -> ((Map<?, ?>) payload).get("keywords") instanceof List<?> list
                        && list.size() != 3));
        verify(inventoryAiCacheService).save(
                eq(InventoryAiCacheService.personalizedKey("바닥 토너", SkinType.DRY, Set.of())),
                argThat(payload -> ((Map<?, ?>) payload).get("keywords") instanceof List<?> list
                        && list.size() == 3));
    }

    @Test
    void doesNotCacheIncompleteKeywordsWhenAiReturnsFewerThanThree() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of());
        when(personalizedAnalysisAiClient.analyze("바닥 토너", List.of(), SkinType.DRY, Set.of()))
                .thenReturn(new PersonalizedAnalysisResult(80, List.of(
                        new PersonalizedAnalysisResult.Keyword("보습", "건성"),
                        new PersonalizedAnalysisResult.Keyword("저자극", "민감성 성분 없음"))));

        AiAnalysisResponse response = inventoryService.getAiAnalysis(9L, 11L);

        assertThat(response.keywords()).hasSize(3);
        verify(inventoryAiCacheService, never()).save(
                eq(InventoryAiCacheService.personalizedKey("바닥 토너", SkinType.DRY, Set.of())), any());
    }

    @Test
    void infersBrandWhenMissingAndPersistsOnProduct() {
        Product product = Product.builder()
                .name("바닥 토너")
                .category(ProductCategory.SKIN_TONER)
                .build();
        ReflectionTestUtils.setField(product, "id", 3L);
        Inventory noBrandInventory = Inventory.builder().member(member).product(product).build();
        ReflectionTestUtils.setField(noBrandInventory, "id", 11L);

        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(noBrandInventory));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.inferBrand("바닥 토너")).thenReturn("이니스프리");
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of("정제수"));
        when(ingredientAiClient.fetchIngredientDetails(List.of("정제수")))
                .thenReturn(Map.of("정제수", new IngredientAiDetail(List.of("용제"), List.of(), "LOW")));

        IngredientAnalysisResponse response = inventoryService.getIngredientAnalysis(9L, 11L);

        assertThat(response.brand()).isEqualTo("이니스프리");
        assertThat(product.getBrand()).isEqualTo("이니스프리");
        verify(ingredientAiClient).inferBrand("바닥 토너");
    }

    @Test
    void cachesUnknownBrandAndDoesNotInferAgain() {
        Product product = Product.builder()
                .name("바닥 토너")
                .category(ProductCategory.SKIN_TONER)
                .build();
        ReflectionTestUtils.setField(product, "id", 3L);
        Inventory noBrandInventory = Inventory.builder().member(member).product(product).build();
        ReflectionTestUtils.setField(noBrandInventory, "id", 11L);

        ObjectNode unknownBrand = new ObjectMapper().createObjectNode().put("brand", "");
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(noBrandInventory));
        when(inventoryAiCacheService.find(InventoryAiCacheService.brandKey("바닥 토너")))
                .thenReturn(Optional.of(unknownBrand));
        when(inventoryAiCacheService.find(InventoryAiCacheService.ingredientKey("바닥 토너")))
                .thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of());

        IngredientAnalysisResponse response = inventoryService.getIngredientAnalysis(9L, 11L);

        assertThat(response.brand()).isNull();
        verify(ingredientAiClient, never()).inferBrand(any());
    }

    /**
     * 브랜드 추론이 실패(빈 응답)하면 캐시에 저장하지 않는다. AI 호출이 일시적으로
     * 실패했을 뿐일 수 있으므로, 빈 브랜드를 캐시하면 30일 동안 다시 시도할 기회를 잃는다.
     */
    @Test
    void doesNotCacheBrandWhenInferenceFails() {
        Product product = Product.builder()
                .name("바닥 토너")
                .category(ProductCategory.SKIN_TONER)
                .build();
        ReflectionTestUtils.setField(product, "id", 3L);
        Inventory noBrandInventory = Inventory.builder().member(member).product(product).build();
        ReflectionTestUtils.setField(noBrandInventory, "id", 11L);

        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(noBrandInventory));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.inferBrand("바닥 토너")).thenReturn(null);
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of());

        IngredientAnalysisResponse response = inventoryService.getIngredientAnalysis(9L, 11L);

        assertThat(response.brand()).isNull();
        verify(inventoryAiCacheService, never()).save(eq(InventoryAiCacheService.brandKey("바닥 토너")), any());
        verify(inventoryAiCacheService, never()).save(eq(InventoryAiCacheService.ingredientKey("바닥 토너")), any());
    }

    @Test
    void fillsDefaultPurposeWhenAiReturnsEmptyList() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of("정제수"));
        when(ingredientAiClient.fetchIngredientDetails(List.of("정제수")))
                .thenReturn(Map.of("정제수", new IngredientAiDetail(List.of(), List.of(), "LOW")));

        IngredientAnalysisResponse response = inventoryService.getIngredientAnalysis(9L, 11L);

        assertThat(response.ingredients()).containsExactly(
                new IngredientAnalysisResponse.IngredientDetail(
                        "정제수", List.of(IngredientPurposeCategory.SKIN_CONDITIONING_AGENT), List.of(),
                        IngredientRiskLevel.LOW));
        verify(ingredientAiClient, never()).inferBrand(any());
    }

    @Test
    void limitsEfficacyTagsToTwoAndDropsUnrecognizedPurposes() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of("정제수"));
        when(ingredientAiClient.fetchIngredientDetails(List.of("정제수")))
                .thenReturn(Map.of("정제수", new IngredientAiDetail(
                        List.of("알수없는목적"), List.of("피부 보습", "피부 보호", "피부 보습"), "LOW")));

        IngredientAnalysisResponse response = inventoryService.getIngredientAnalysis(9L, 11L);

        assertThat(response.ingredients()).containsExactly(
                new IngredientAnalysisResponse.IngredientDetail(
                        "정제수",
                        List.of(IngredientPurposeCategory.SKIN_CONDITIONING_AGENT),
                        List.of(SkinEfficacyTag.MOISTURIZING, SkinEfficacyTag.PROTECTION),
                        IngredientRiskLevel.LOW));
    }
}
