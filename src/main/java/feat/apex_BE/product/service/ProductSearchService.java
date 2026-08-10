package feat.apex_BE.product.service;

import feat.apex_BE.global.exception.CustomException;
import feat.apex_BE.global.exception.ErrorCode;
import feat.apex_BE.product.client.DetailPageScraper;
import feat.apex_BE.product.client.GoogleVisionOcrClient;
import feat.apex_BE.product.client.NaverShoppingClient;
import feat.apex_BE.product.dto.response.NaverShoppingSearchResponse;
import feat.apex_BE.product.dto.response.ProductSearchResponse;
import feat.apex_BE.product.util.IngredientTextExtractor;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a cosmetic-product search: finds the single most relevant
 * product for a keyword via Naver's Shopping Search API, then attempts to
 * enrich it with an ingredient list scraped/OCR'd from the product's detail
 * page. Ingredient extraction is best-effort - any failure there degrades
 * gracefully to {@code ingredients = null} rather than failing the request.
 */
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);

    private final NaverShoppingClient naverShoppingClient;
    private final DetailPageScraper detailPageScraper;
    private final GoogleVisionOcrClient googleVisionOcrClient;
    private final IngredientTextExtractor ingredientTextExtractor;

    public ProductSearchResponse search(String query) {
        NaverShoppingSearchResponse.Item product = naverShoppingClient.searchTopProduct(query)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        String ingredients = extractIngredients(product.link());

        return new ProductSearchResponse(
                product.title(),
                product.image(),
                parsePrice(product.lprice()),
                ingredients
        );
    }

    private String extractIngredients(String productDetailUrl) {
        try {
            List<String> imageUrls = detailPageScraper.extractDetailImageUrls(productDetailUrl);
            for (String imageUrl : imageUrls) {
                Optional<String> ocrText = googleVisionOcrClient.extractText(imageUrl);
                if (ocrText.isEmpty()) {
                    continue;
                }
                Optional<String> ingredients = ingredientTextExtractor.extract(List.of(ocrText.get()));
                if (ingredients.isPresent()) {
                    return ingredients.get();
                }
            }
        } catch (Exception e) {
            log.warn("성분 추출 과정에서 예외가 발생했습니다. 성분은 null로 응답합니다. reason={}", e.getMessage());
        }
        return null;
    }

    private Long parsePrice(String rawPrice) {
        if (rawPrice == null || rawPrice.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(rawPrice.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
