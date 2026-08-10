package feat.apex_BE.product.dto.response;

/**
 * API response for a single cosmetic product search result.
 *
 * @param ingredients ingredient ("전성분") text extracted via OCR from the
 *                     product detail page; {@code null} if it could not be
 *                     found (this is treated as a best-effort field)
 */
public record ProductSearchResponse(
        String productName,
        String productImage,
        Long productPrice,
        String ingredients
) {
}
