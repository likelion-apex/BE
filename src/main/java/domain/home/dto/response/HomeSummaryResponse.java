package domain.home.dto.response;

import domain.inventory.dto.response.FavoriteInventoryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "홈 요약 조회 결과")
public record HomeSummaryResponse(
        @Schema(description = "오늘의 컨디션 체크") TodayConditionResponse todayCondition,
        @Schema(description = "오늘의 활성 루틴 (없으면 null)") TodayRoutineResponse todayRoutine,
        @Schema(description = "즐겨찾는 화장품 목록 (최대 4개)") FavoriteInventoryResponse favoriteInventory
) {
}