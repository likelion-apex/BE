package domain.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
        keywordPayload.putArray("keywords").addObject().put("keyword", "보습").put("reason", "건성");
        when(inventoryAiCacheService.find(InventoryAiCacheService.personalizedKey(
                "바닥 토너", SkinType.DRY, Set.of())))
                .thenReturn(Optional.of(keywordPayload));

        AiAnalysisResponse response = inventoryService.getAiAnalysis(9L, 11L);

        assertThat(response.score()).isEqualTo(87);
        assertThat(response.keywords()).containsExactly(new AiAnalysisResponse.AnalysisKeyword("보습", "건성"));
        verify(personalizedAnalysisAiClient, never()).analyze(any(), any(), any(), any());
        verify(ingredientAiClient, never()).fetchIngredientNames(any());
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
        assertThat(response.keywords()).containsExactly(new AiAnalysisResponse.AnalysisKeyword("보습", "건성"));
    }

    @Test
    void personalizedAnalysisReturnsDefaultScoreWithEmptyKeywordsWhenAiReturnsNull() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of());
        when(personalizedAnalysisAiClient.analyze("바닥 토너", List.of(), SkinType.DRY, Set.of()))
                .thenReturn(null);

        AiAnalysisResponse response = inventoryService.getAiAnalysis(9L, 11L);

        assertThat(response.score()).isEqualTo(70);
        assertThat(response.keywords()).isEmpty();
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

    @Test
    void savesUnknownBrandMarkerWhenInferenceFails() {
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
        verify(inventoryAiCacheService).save(eq(InventoryAiCacheService.brandKey("바닥 토너")), eq(Map.of("brand", "")));
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
