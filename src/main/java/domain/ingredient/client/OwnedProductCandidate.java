package domain.ingredient.client;

import domain.inventory.ProductCategory;

/**
 * 루틴 개선 방향 제시(4.6)에서 AI에게 "이 목록 중에서만 골라라"라고 전달하는 사용자 보유 제품 후보.
 */
public record OwnedProductCandidate(Long productId, String productName, ProductCategory category) {
}
