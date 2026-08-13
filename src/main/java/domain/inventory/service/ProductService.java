package domain.inventory.service;

import domain.cosmetic.client.KakaoImageClient;
import domain.inventory.Product;
import domain.inventory.ProductRepository;
import domain.inventory.client.OpenAiCategoryClassifier;
import domain.inventory.dto.response.ProductSearchResponse;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final KakaoImageClient kakaoImageClient;
    private final OpenAiCategoryClassifier categoryClassifier;

    @Transactional(readOnly = true)
    public ProductSearchResponse search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return ProductSearchResponse.from(List.of());
        }
        return ProductSearchResponse.from(productRepository.findByNameContainingIgnoreCase(keyword.trim()));
    }

    /**
     * productId가 있으면 기존 상품을 조회하고, 없고 productName만 있으면 마스터 DB에서 이름으로 찾거나
     * 없을 경우 Kakao 이미지 검색 + AI 카테고리 분류로 신규 상품을 등록한다.
     * (명세서의 화장품 검색 API는 등록/자동 생성을 하지 않으므로, 신규 상품 등록은 인벤토리 추가 시점에만 일어난다.)
     */
    public Product findOrCreate(Long productId, String productName) {
        if (productId != null) {
            return productRepository.findById(productId)
                    .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        }
        if (productName == null || productName.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "productId 또는 productName 중 하나는 필수입니다.");
        }
        String trimmedName = productName.trim();
        return productRepository.findByName(trimmedName)
                .orElseGet(() -> createProduct(trimmedName));
    }

    private Product createProduct(String productName) {
        String imageUrl = kakaoImageClient.searchImageUrl(productName);
        Product product = Product.builder()
                .name(productName)
                .category(categoryClassifier.classify(productName))
                .imageUrl(imageUrl)
                .build();
        return productRepository.save(product);
    }
}
