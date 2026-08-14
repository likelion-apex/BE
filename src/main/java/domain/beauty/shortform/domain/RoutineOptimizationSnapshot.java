package domain.beauty.shortform.domain;

import java.util.List;

public record RoutineOptimizationSnapshot(
        int newProductCount,
        int compatibleCount,
        int replacedCount,
        int missingCount,
        String summary,
        List<OptimizedStep> steps
) {

    public record OptimizedStep(
            long sourceResultId,
            int order,
            OptimizationStatus status,
            Long inventoryId,
            Long productId,
            String category,
            String productName,
            String brand,
            String imageUrl,
            String reason
    ) {
    }
}
