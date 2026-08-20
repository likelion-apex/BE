package domain.home.service;

import domain.beauty.shortform.application.ShortformRoutineTypeResolver;
import domain.home.dto.request.DailyConditionRequest;
import domain.home.dto.response.HomeSummaryResponse;
import domain.home.dto.response.TodayConditionResponse;
import domain.home.dto.response.TodayRoutineResponse;
import domain.inventory.dto.response.FavoriteInventoryResponse;
import domain.inventory.service.InventoryService;
import domain.member.Member;
import domain.member.MemberRepository;
import domain.routine.domain.DailyCondition;
import domain.routine.domain.RoutineStatus;
import domain.routine.domain.RoutineType;
import domain.routine.repository.DailyConditionRepository;
import domain.routine.repository.RoutineRepository;
import global.exception.CustomException;
import global.exception.ErrorCode;
import global.util.PublicUrlResolver;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    /** 와이어프레임상 홈 카드는 4개까지만 노출하고 나머지는 인벤토리 탭 "전체보기"로 유도한다. */
    private static final int FAVORITE_LIMIT = 4;

    private final ShortformRoutineTypeResolver routineTypeResolver;
    private final RoutineRepository routineRepository;
    private final DailyConditionRepository dailyConditionRepository;
    private final MemberRepository memberRepository;
    private final InventoryService inventoryService;
    private final PublicUrlResolver publicUrlResolver;

    public HomeSummaryResponse getSummary(Long memberId) {
        TodayConditionResponse todayCondition = dailyConditionRepository
                .findByMemberIdAndLogDate(memberId, LocalDate.now())
                .map(TodayConditionResponse::from)
                .orElseGet(TodayConditionResponse::notLogged);

        RoutineType routineType = routineTypeResolver.resolve(null);
        TodayRoutineResponse todayRoutine = routineRepository
                .findByMemberIdAndStatusAndRoutineType(memberId, RoutineStatus.ACTIVE, routineType)
                .map(routine -> TodayRoutineResponse.from(routine, publicUrlResolver))
                .orElse(null);

        FavoriteInventoryResponse favoriteInventory = inventoryService.getFavorites(memberId, FAVORITE_LIMIT);

        return new HomeSummaryResponse(todayCondition, todayRoutine, favoriteInventory);
    }

    @Transactional
    public TodayConditionResponse updateCondition(Long memberId, DailyConditionRequest request) {
        LocalDate today = LocalDate.now();

        if (request.condition() == null) {
            dailyConditionRepository.deleteByMemberIdAndLogDate(memberId, today);
            return TodayConditionResponse.notLogged();
        }

        DailyCondition dailyCondition = dailyConditionRepository.findByMemberIdAndLogDate(memberId, today)
                .orElse(null);
        if (dailyCondition != null) {
            dailyCondition.update(request.condition(), request.memo());
        } else {
            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
            dailyCondition = dailyConditionRepository.save(
                    new DailyCondition(member, today, request.condition(), request.memo()));
        }

        return TodayConditionResponse.from(dailyCondition);
    }
}