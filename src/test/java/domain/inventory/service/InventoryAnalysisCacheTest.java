package domain.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import domain.inventory.Inventory;
import domain.inventory.InventoryRepository;
import domain.inventory.Product;
import domain.inventory.ProductCategory;
import domain.inventory.ai.IngredientAiClient;
import domain.inventory.ai.InventoryAiCacheService;
import domain.inventory.ai.PersonalizedAnalysisAiClient;
import domain.inventory.client.PersonalizedAnalysisResult;
import domain.inventory.dto.response.AiAnalysisResponse;
import domain.inventory.dto.response.IngredientAnalysisResponse;
import domain.member.Member;
import domain.member.MemberRepository;
import domain.member.Provider;
import domain.member.Role;
import domain.member.SkinType;
import global.exception.CustomException;
import global.exception.ErrorCode;
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
                inventoryAiCacheService);
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
                .putArray("purposes").add("기제(용매)");
        when(inventoryAiCacheService.find(InventoryAiCacheService.ingredientKey("바닥 토너")))
                .thenReturn(Optional.of(payload));

        IngredientAnalysisResponse response = inventoryService.getIngredientAnalysis(9L, 11L);

        assertThat(response.ingredients()).containsExactly(
                new IngredientAnalysisResponse.IngredientPurpose("정제수", List.of("기제(용매)")));
        verify(ingredientAiClient, never()).fetchIngredientNames(any());
        verify(ingredientAiClient, never()).fetchIngredientPurposes(any());
    }

    @Test
    void personalizedAnalysisUsesCacheAndSkipsAi() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("score", 80);
        payload.putArray("keywords").addObject().put("keyword", "보습").put("reason", "건성");
        when(inventoryAiCacheService.find(InventoryAiCacheService.personalizedKey(
                "바닥 토너", SkinType.DRY, Set.of())))
                .thenReturn(Optional.of(payload));

        AiAnalysisResponse response = inventoryService.getAiAnalysis(9L, 11L);

        assertThat(response.score()).isEqualTo(80);
        verify(personalizedAnalysisAiClient, never()).analyze(any(), any(), any(), any());
    }

    @Test
    void ingredientAnalysisStoresCacheAfterAiHit() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of("정제수"));
        when(ingredientAiClient.fetchIngredientPurposes(List.of("정제수")))
                .thenReturn(Map.of("정제수", List.of("기제(용매)")));

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
    void ingredientAnalysisStillReturnsWhenCacheFails() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(inventoryAiCacheService.find(any())).thenThrow(new RuntimeException("inventory_ai_caches missing"));
        when(ingredientAiClient.fetchIngredientNames("바닥 토너")).thenReturn(List.of("정제수"));
        when(ingredientAiClient.fetchIngredientPurposes(List.of("정제수")))
                .thenReturn(Map.of("정제수", List.of("기제(용매)")));
        doThrow(new RuntimeException("read-only transaction")).when(inventoryAiCacheService).save(any(), any());

        IngredientAnalysisResponse response = inventoryService.getIngredientAnalysis(9L, 11L);

        assertThat(response.ingredients()).containsExactly(
                new IngredientAnalysisResponse.IngredientPurpose("정제수", List.of("기제(용매)")));
    }

    @Test
    void personalizedAnalysisStillReturnsWhenCacheFails() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));
        when(inventoryAiCacheService.find(any())).thenThrow(new RuntimeException("inventory_ai_caches missing"));
        when(personalizedAnalysisAiClient.analyze("바닥 토너", List.of(), SkinType.DRY, Set.of()))
                .thenReturn(new PersonalizedAnalysisResult(
                        80, List.of(new PersonalizedAnalysisResult.Keyword("보습", "건성"))));
        doThrow(new RuntimeException("read-only transaction")).when(inventoryAiCacheService).save(any(), any());

        AiAnalysisResponse response = inventoryService.getAiAnalysis(9L, 11L);

        assertThat(response.score()).isEqualTo(80);
        assertThat(response.keywords()).containsExactly(new AiAnalysisResponse.AnalysisKeyword("보습", "건성"));
    }

    @Test
    void personalizedAnalysisStillFailsWithInventory003WhenAiReturnsNull() {
        when(inventoryRepository.findByIdAndMemberId(11L, 9L)).thenReturn(Optional.of(inventory));
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));
        when(inventoryAiCacheService.find(any())).thenReturn(Optional.empty());
        when(personalizedAnalysisAiClient.analyze("바닥 토너", List.of(), SkinType.DRY, Set.of()))
                .thenReturn(null);

        assertThatThrownBy(() -> inventoryService.getAiAnalysis(9L, 11L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_ANALYSIS_FAILED);
    }
}
