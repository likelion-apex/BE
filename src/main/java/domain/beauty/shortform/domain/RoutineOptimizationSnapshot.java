package domain.beauty.shortform.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RoutineOptimizationSnapshot(
        int newProductCount,
        int replacedCount,
        int missingCount,
        String summary,
        List<OptimizedStep> steps
) {

    public record OptimizedStep(
            long sourceResultId,
            int order,
            @Schema(allowableValues = {"VIDEO_PRODUCT", "REPLACED"})
            OptimizationStatus status,
            Long inventoryId,
            Long productId,
            String category,
            String productName,
            String replaceName,
            String brand,
            String imageUrl,
            String reason
    ) {
    }
}
