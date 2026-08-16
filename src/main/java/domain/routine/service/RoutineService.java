package domain.routine.service;

import domain.beauty.shortform.application.ShortformRoutineTypeResolver;
import domain.routine.domain.DailyCondition;
import domain.routine.domain.Routine;
import domain.routine.domain.RoutineLog;
import domain.routine.domain.RoutineLogStep;
import domain.routine.domain.RoutineStatus;
import domain.routine.domain.RoutineStep;
import domain.routine.domain.RoutineType;
import domain.routine.dto.response.ArchivedRoutineListResponse;
import domain.routine.dto.response.CalendarMonthResponse;
import domain.routine.dto.response.DailyLogDetailResponse;
import domain.routine.dto.response.DailyRoutineResponse;
import domain.routine.dto.response.RoutineDetailResponse;
import domain.routine.repository.DailyConditionRepository;
import domain.routine.repository.RoutineLogRepository;
import domain.routine.repository.RoutineLogStepRepository;
import domain.routine.repository.RoutineRepository;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoutineService {

    private final ShortformRoutineTypeResolver routineTypeResolver;
    private final RoutineRepository routineRepository;
    private final RoutineLogRepository routineLogRepository;
    private final RoutineLogStepRepository routineLogStepRepository;
    private final DailyConditionRepository dailyConditionRepository;

    @Transactional
    public DailyRoutineResponse getDailyRoutine(Long memberId) {
        RoutineType routineType = routineTypeResolver.resolve(null);
        Routine routine = routineRepository
                .findByMemberIdAndStatusAndRoutineType(memberId, RoutineStatus.ACTIVE, routineType)
                .orElse(null);
        if (routine == null) {
            return null;
        }

        LocalDate today = LocalDate.now();
        RoutineLog routineLog = routineLogRepository
                .findByMemberIdAndLogDateAndRoutineId(memberId, today, routine.getId())
                .orElseGet(() -> createTodayLog(routine, today));

        return DailyRoutineResponse.from(routine, routineLog);
    }

    @Transactional
    public DailyRoutineResponse updateStepCompletion(Long memberId, Long stepId, boolean completed) {
        RoutineLogStep step = routineLogStepRepository.findByIdAndRoutineLog_Member_Id(stepId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTINE_LOG_STEP_NOT_FOUND));
        step.updateCompleted(completed);

        RoutineLog routineLog = step.getRoutineLog();
        return DailyRoutineResponse.from(routineLog.getRoutine(), routineLog);
    }

    @Transactional
    public DailyRoutineResponse completeToday(Long memberId) {
        RoutineLog routineLog = findTodayRoutineLog(memberId);
        boolean allStepsCompleted = routineLog.getSteps().stream().allMatch(RoutineLogStep::isCompleted);
        if (!allStepsCompleted) {
            throw new CustomException(ErrorCode.ROUTINE_LOG_STEPS_INCOMPLETE);
        }
        routineLog.complete();
        return DailyRoutineResponse.from(routineLog.getRoutine(), routineLog);
    }

    @Transactional
    public DailyRoutineResponse completeAllSteps(Long memberId) {
        RoutineLog routineLog = findTodayRoutineLog(memberId);
        routineLog.getSteps().forEach(step -> step.updateCompleted(true));
        return DailyRoutineResponse.from(routineLog.getRoutine(), routineLog);
    }

    public CalendarMonthResponse getCalendarMonth(Long memberId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<RoutineLog> logs = routineLogRepository.findByMemberIdAndLogDateBetween(memberId, start, end);
        return CalendarMonthResponse.from(year, month, logs);
    }

    public DailyLogDetailResponse getDailyLogDetail(Long memberId, LocalDate date) {
        DailyCondition dailyCondition = dailyConditionRepository.findByMemberIdAndLogDate(memberId, date).orElse(null);
        List<RoutineLog> logs = routineLogRepository.findByMemberIdAndLogDate(memberId, date);
        return DailyLogDetailResponse.from(date, dailyCondition, logs);
    }

    public ArchivedRoutineListResponse getArchivedRoutines(Long memberId, String period, String sort) {
        List<Routine> routines = switch (period) {
            case "3M" -> routineRepository.findByMemberIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                    memberId, RoutineStatus.ARCHIVED, LocalDateTime.now().minusMonths(3));
            case "6M" -> routineRepository.findByMemberIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                    memberId, RoutineStatus.ARCHIVED, LocalDateTime.now().minusMonths(6));
            default -> routineRepository.findByMemberIdAndStatusOrderByCreatedAtDesc(memberId, RoutineStatus.ARCHIVED);
        };
        return ArchivedRoutineListResponse.from(routines);
    }

    public RoutineDetailResponse getRoutineDetail(Long memberId, Long routineId) {
        Routine routine = routineRepository.findByIdAndMemberId(routineId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTINE_NOT_FOUND));
        return RoutineDetailResponse.from(routine);
    }

    private RoutineLog findTodayRoutineLog(Long memberId) {
        RoutineType routineType = routineTypeResolver.resolve(null);
        Routine routine = routineRepository
                .findByMemberIdAndStatusAndRoutineType(memberId, RoutineStatus.ACTIVE, routineType)
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTINE_LOG_NOT_FOUND));
        LocalDate today = LocalDate.now();
        return routineLogRepository
                .findByMemberIdAndLogDateAndRoutineId(memberId, today, routine.getId())
                .orElseGet(() -> createTodayLog(routine, today));
    }

    private RoutineLog createTodayLog(Routine routine, LocalDate today) {
        RoutineLog log = new RoutineLog(routine, routine.getMember(), today);
        for (RoutineStep step : routine.getSteps()) {
            log.addStep(new RoutineLogStep(log, step.getId(), step.getOrder()));
        }
        return routineLogRepository.save(log);
    }
}