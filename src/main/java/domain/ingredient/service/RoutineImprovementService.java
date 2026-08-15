package domain.ingredient.service;

import domain.cosmetic.client.OpenAiIngredientClient;
import domain.ingredient.client.OpenAiRoutineImprovementClient;
import domain.ingredient.client.OwnedProductCandidate;
import domain.ingredient.client.RoutineImprovementResult;
import domain.ingredient.client.RoutineImprovementResult.Match;
import domain.ingredient.dto.response.RoutineImprovementResponse;
import domain.ingredient.dto.response.RoutineImprovementResponse.Duplicate;
import domain.ingredient.dto.response.RoutineImprovementResponse.MatchedProduct;
import domain.ingredient.dto.response.RoutineImprovementResponse.Relation;
import domain.inventory.Inventory;
import domain.inventory.InventoryRepository;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 루틴 개선 방향 제시(4.6). 로그인 회원의 인벤토리 전체를 기준으로, 쿼리로 전달된 제품명(및 추정 성분)과
 * 시너지·충돌·중복(대체) 관계를 분석한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoutineImprovementService {

    private static final String NO_SYNERGY_MESSAGE = "함께 쓰면 좋은 시너지 조합을 찾지 못했어요.";
    private static final String NO_CONFLICT_MESSAGE = "특별히 충돌하는 보유 제품은 없어요.";
    private static final String NO_DUPLICATE_MESSAGE = "보유 제품과 겹치는 제품은 없어요.";

    private final InventoryRepository inventoryRepository;
    private final OpenAiIngredientClient openAiIngredientClient;
    private final OpenAiRoutineImprovementClient routineImprovementClient;

    public RoutineImprovementResponse analyze(Long memberId, String productName) {
        if (productName == null || productName.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "productName은 필수입니다.");
        }
        String trimmedName = productName.trim();

        List<Inventory> ownedInventories = inventoryRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId);
        if (ownedInventories.isEmpty()) {
            return emptyResponse(trimmedName);
        }

        List<OwnedProductCandidate> candidates = ownedInventories.stream()
                .map(inventory -> new OwnedProductCandidate(
                        inventory.getProduct().getId(), inventory.getProduct().getName(), inventory.getProduct().getCategory()))
                .distinct()
                .toList();
        Map<Long, String> productNameById = candidates.stream()
                .collect(Collectors.toMap(OwnedProductCandidate::productId, OwnedProductCandidate::productName));

        List<String> ingredientNames = openAiIngredientClient.fetchIngredientNames(trimmedName);

        RoutineImprovementResult result = routineImprovementClient.analyze(trimmedName, ingredientNames, candidates);
        if (result == null) {
            throw new CustomException(ErrorCode.AI_ANALYSIS_FAILED);
        }

        Function<Long, String> nameResolver = productId -> productNameById.getOrDefault(productId, "알 수 없는 제품");
        Relation synergy = toRelation(result.synergy(), NO_SYNERGY_MESSAGE, nameResolver);
        Relation conflict = toRelation(result.conflict(), NO_CONFLICT_MESSAGE, nameResolver);
        Duplicate duplicate = toDuplicate(result.duplicate(), nameResolver);

        return new RoutineImprovementResponse(trimmedName, synergy, conflict, duplicate);
    }

    private RoutineImprovementResponse emptyResponse(String productName) {
        return new RoutineImprovementResponse(
                productName,
                new Relation(false, NO_SYNERGY_MESSAGE, null),
                new Relation(false, NO_CONFLICT_MESSAGE, null),
                new Duplicate(false, NO_DUPLICATE_MESSAGE, null)
        );
    }

    private Relation toRelation(Match match, String noneMessage, Function<Long, String> nameResolver) {
        if (match == null) {
            return new Relation(false, noneMessage, null);
        }
        return new Relation(true, match.message(), new MatchedProduct(match.productId(), nameResolver.apply(match.productId())));
    }

    private Duplicate toDuplicate(Match match, Function<Long, String> nameResolver) {
        if (match == null) {
            return new Duplicate(false, NO_DUPLICATE_MESSAGE, null);
        }
        return new Duplicate(true, match.message(), new MatchedProduct(match.productId(), nameResolver.apply(match.productId())));
    }
}
