package domain.beauty.shortform.client;

import java.util.List;

public record ProductEnrichmentInput(
        boolean verificationPass,
        List<Product> products
) {
    public record Product(
            String requestKey,
            String category,
            String rawBrand,
            String rawProductName,
            String rawVariant,
            String identityEvidenceText
    ) {
    }
}
