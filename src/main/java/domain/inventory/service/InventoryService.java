package domain.inventory.service;

import domain.cosmetic.client.OpenAiIngredientClient;
import domain.inventory.Inventory;
import domain.inventory.InventoryRepository;
import domain.inventory.Product;
import domain.inventory.client.OpenAiPersonalizedAnalysisClient;
import domain.inventory.client.PersonalizedAnalysisResult;
import domain.inventory.dto.request.InventoryCreateRequest;
import domain.inventory.dto.response.AiAnalysisResponse;
import domain.inventory.dto.response.FavoriteInventoryResponse;
import domain.inventory.dto.response.FavoriteUpdateResponse;
import domain.inventory.dto.response.IngredientAnalysisResponse;
import domain.inventory.dto.response.InventoryCreateResponse;
import domain.inventory.dto.response.InventoryDeleteResponse;
import domain.inventory.dto.response.InventoryListResponse;
import domain.member.Member;
import domain.member.MemberRepository;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryService {

    private static final int DEFAULT_FAVORITE_LIMIT = 4;

    private final InventoryRepository inventoryRepository;
    private final MemberRepository memberRepository;
    private final ProductService productService;
    private final OpenAiIngredientClient openAiIngredientClient;
    private final OpenAiPersonalizedAnalysisClient personalizedAnalysisClient;

    @Transactional(readOnly = true)
    public FavoriteInventoryResponse getFavorites(Long memberId, Integer limit) {
        int size = (limit == null || limit <= 0) ? DEFAULT_FAVORITE_LIMIT : limit;
        long totalFavoriteCount = inventoryRepository.countByMemberIdAndFavoriteTrue(memberId);
        List<Inventory> favorites = inventoryRepository
                .findAllByMemberIdAndFavoriteTrueOrderByCreatedAtDesc(memberId, PageRequest.of(0, size));
        return FavoriteInventoryResponse.of(totalFavoriteCount, favorites);
    }

    @Transactional(readOnly = true)
    public InventoryListResponse getAll(Long memberId) {
        return InventoryListResponse.from(inventoryRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId));
    }

    public InventoryCreateResponse create(Long memberId, InventoryCreateRequest request) {
        Member member = findMember(memberId);
        Product product = productService.findOrCreate(request.productId(), request.productName());
        Inventory inventory = Inventory.builder()
                .member(member)
                .product(product)
                .ownType(request.ownType())
                .build();
        return InventoryCreateResponse.from(inventoryRepository.save(inventory));
    }

    public InventoryDeleteResponse remove(Long memberId, Long inventoryId) {
        Inventory inventory = findOwnedInventory(memberId, inventoryId);
        inventoryRepository.delete(inventory);
        return InventoryDeleteResponse.of(inventoryId);
    }

    public FavoriteUpdateResponse updateFavorite(Long memberId, Long inventoryId, boolean isFavorite) {
        Inventory inventory = findOwnedInventory(memberId, inventoryId);
        inventory.updateFavorite(isFavorite);
        return FavoriteUpdateResponse.from(inventory);
    }

    @Transactional(readOnly = true)
    public AiAnalysisResponse getAiAnalysis(Long memberId, Long inventoryId) {
        Inventory inventory = findOwnedInventory(memberId, inventoryId);
        Member member = findMember(memberId);
        String productName = inventory.getProduct().getName();
        List<String> ingredientNames = openAiIngredientClient.fetchIngredientNames(productName);

        PersonalizedAnalysisResult result = personalizedAnalysisClient.analyze(
                productName, ingredientNames, member.getSkinType(), member.getSkinConcerns());
        if (result == null) {
            throw new CustomException(ErrorCode.AI_ANALYSIS_FAILED);
        }

        List<AiAnalysisResponse.AnalysisKeyword> keywords = result.keywords().stream()
                .map(keyword -> new AiAnalysisResponse.AnalysisKeyword(keyword.keyword(), keyword.reason()))
                .toList();
        return new AiAnalysisResponse(inventory.getId(), productName, result.score(), keywords, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public IngredientAnalysisResponse getIngredientAnalysis(Long memberId, Long inventoryId) {
        Inventory inventory = findOwnedInventory(memberId, inventoryId);
        String productName = inventory.getProduct().getName();
        List<String> ingredientNames = openAiIngredientClient.fetchIngredientNames(productName);
        Map<String, List<String>> purposesByName = openAiIngredientClient.fetchIngredientPurposes(ingredientNames);

        List<IngredientAnalysisResponse.IngredientPurpose> ingredients = ingredientNames.stream()
                .map(name -> new IngredientAnalysisResponse.IngredientPurpose(
                        name, purposesByName.getOrDefault(name, List.of())))
                .toList();
        return new IngredientAnalysisResponse(inventory.getId(), productName, ingredients);
    }

    private Inventory findOwnedInventory(Long memberId, Long inventoryId) {
        return inventoryRepository.findByIdAndMemberId(inventoryId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVENTORY_NOT_FOUND));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
