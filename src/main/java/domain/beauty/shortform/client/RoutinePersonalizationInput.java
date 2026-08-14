package domain.beauty.shortform.client;

import java.util.List;

public record RoutinePersonalizationInput(
        MemberProfile member,
        VideoContext video,
        List<VideoStep> steps,
        List<InventoryProduct> inventory
) {
    public record MemberProfile(String nickname, String skinType, List<String> skinConcerns) {
    }

    public record VideoContext(String videoId, String summary, List<String> warnings) {
    }

    public record VideoStep(
            int order,
            String category,
            String brand,
            String productName,
            String purpose,
            String evidenceSummary,
            double confidence,
            Long matchedProductId
    ) {
    }

    public record InventoryProduct(
            Long inventoryId,
            Long productId,
            String category,
            String brand,
            String productName
    ) {
    }
}
