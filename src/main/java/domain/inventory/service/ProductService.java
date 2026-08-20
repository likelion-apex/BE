package domain.inventory.service;

import domain.inventory.CategoryImageResolver;
import domain.inventory.Product;
import domain.inventory.ProductCategory;
import domain.inventory.ProductNameNormalizer;
import domain.inventory.ProductRepository;
import domain.inventory.cache.PopularProductCache;
import domain.inventory.client.OpenAiCategoryClassifier;
import domain.inventory.dto.response.ProductListResponse;
import domain.inventory.dto.response.ProductSearchResponse;
import global.exception.CustomException;
import global.exception.ErrorCode;
import global.util.PublicUrlResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryImageResolver categoryImageResolver;
    private final OpenAiCategoryClassifier categoryClassifier;
    private final PopularProductCache popularProductCache;
    private final PublicUrlResolver publicUrlResolver;

    @Transactional(readOnly = true)
    public ProductSearchResponse search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return ProductSearchResponse.from(List.of(), publicUrlResolver);
        }
        return ProductSearchResponse.from(
                productRepository.findByNameContainingIgnoreCase(keyword.trim()), publicUrlResolver);
    }

    @Transactional(readOnly = true)
    public ProductListResponse listAll() {
        return ProductListResponse.from(productRepository.findAllByOrderByIdAsc(), publicUrlResolver);
    }

    @Transactional(readOnly = true)
    public Product getById(Long productId) {
        return popularProductCache.find(productId)
                .orElseGet(() -> productRepository.findById(productId)
                        .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND)));
    }

    /**
     * productName으로 마스터 DB에서 기존 상품을 찾거나, 없으면 AI 카테고리 분류 + 카테고리별 정적 이미지로 신규 상품을 등록한다.
     * (명세서의 화장품 검색 API는 등록/자동 생성을 하지 않으므로, 신규 상품 등록은 인벤토리 추가 시점에만 일어난다.)
     */
    public Product findOrCreate(String productName) {
        if (productName == null || productName.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "productName은 필수입니다.");
        }
        String trimmedName = productName.trim();
        String normalizedName = ProductNameNormalizer.canonicalKey(trimmedName);
        return productRepository.findByNormalizedName(normalizedName)
                .or(() -> productRepository.findByName(trimmedName))
                .orElseGet(() -> createProduct(trimmedName));
    }

    private Product createProduct(String productName) {
        ProductCategory category = categoryClassifier.classify(productName);
        Product product = Product.builder()
                .name(productName)
                .category(category)
                .imageUrl(categoryImageResolver.resolve(category))
                .build();
        return productRepository.save(product);
    }
}
