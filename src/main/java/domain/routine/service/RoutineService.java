package domain.routine.service;

import domain.beauty.shortform.application.ShortformAnalysisJsonMapper;
import domain.beauty.shortform.application.ShortformAnalysisSnapshotNormalizer;
import domain.beauty.shortform.application.ShortformRoutineTypeResolver;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot;
import domain.beauty.shortform.domain.RoutineSaveType;
import domain.beauty.shortform.domain.ShortformAnalysis;
import domain.beauty.shortform.domain.ShortformAnalysisRepository;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot;
import domain.ingredient.domain.InteractionType;
import domain.ingredient.dto.request.ProductCompatibilityRequest;
import domain.ingredient.dto.response.AnalysisReason;
import domain.ingredient.dto.response.ProductCompatibilityResponse;
import domain.ingredient.dto.response.ProductCompatibilityResponse.CompatibilityResult;
import domain.ingredient.dto.response.SkinAnalysisResponse;
import domain.ingredient.service.ProductCompatibilityService;
import domain.ingredient.service.SkinAnalysisService;
import domain.inventory.Inventory;
import domain.inventory.InventoryRepository;
import domain.inventory.Product;
import domain.inventory.ProductCategory;
import domain.member.Member;
import domain.member.MemberRepository;
import domain.routine.domain.DailyCondition;
import domain.routine.domain.Routine;
import domain.routine.domain.RoutineLog;
import domain.routine.domain.RoutineLogStep;
import domain.routine.domain.RoutineStatus;
import domain.routine.domain.RoutineStep;
import domain.routine.domain.RoutineType;
import domain.routine.dto.request.RoutineCreateRequest;
import domain.routine.dto.request.RoutineCreateRequest.RoutineStepCreateRequest;
import domain.routine.dto.response.ArchivedRoutineListResponse;
import domain.routine.dto.response.CalendarMonthResponse;
import domain.routine.dto.response.DailyLogDetailResponse;
import domain.routine.dto.response.DailyRoutineResponse;
import domain.routine.dto.response.RoutineCreateResponse;
import domain.routine.dto.response.RoutineDeleteResponse;
import domain.routine.dto.response.RoutineDetailResponse;
import domain.routine.dto.response.RoutineDetailResponse.AiBriefing;
import domain.routine.dto.response.RoutineGenerationResponse;
import domain.routine.dto.response.RoutineGenerationResponse.GeneratedStep;
import domain.routine.repository.DailyConditionRepository;
import domain.routine.repository.RoutineLogRepository;
import domain.routine.repository.RoutineLogStepRepository;
import domain.routine.repository.RoutineRepository;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoutineService {

    private final ShortformRoutineTypeResolver routineTypeResolver;
    private final RoutineRepository routineRepository;
    private final RoutineLogRepository routineLogRepository;
    private final RoutineLogStepRepository routineLogStepRepository;
    private final DailyConditionRepository dailyConditionRepository;
    private final MemberRepository memberRepository;
    private final InventoryRepository inventoryRepository;
    private final SkinAnalysisService skinAnalysisService;
    private final ProductCompatibilityService productCompatibilityService;
    private final ShortformAnalysisRepository shortformAnalysisRepository;
    private final ShortformAnalysisJsonMapper shortformAnalysisJsonMapper;
    private final ShortformAnalysisSnapshotNormalizer shortformAnalysisSnapshotNormalizer;

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

        return DailyRoutineResponse.from(routine, routineLog, resolveAiBriefing(routine));
    }

    @Transactional
    public DailyRoutineResponse updateStepCompletion(Long memberId, Long stepId, boolean completed) {
        RoutineLogStep step = routineLogStepRepository.findByIdAndRoutineLog_Member_Id(stepId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTINE_LOG_STEP_NOT_FOUND));
        step.updateCompleted(completed);

        RoutineLog routineLog = step.getRoutineLog();
        return DailyRoutineResponse.from(routineLog.getRoutine(), routineLog, resolveAiBriefing(routineLog.getRoutine()));
    }

    @Transactional
    public DailyRoutineResponse completeToday(Long memberId) {
        RoutineLog routineLog = findTodayRoutineLog(memberId);
        boolean allStepsCompleted = routineLog.getSteps().stream().allMatch(RoutineLogStep::isCompleted);
        if (!allStepsCompleted) {
            throw new CustomException(ErrorCode.ROUTINE_LOG_STEPS_INCOMPLETE);
        }
        routineLog.complete();
        return DailyRoutineResponse.from(routineLog.getRoutine(), routineLog, resolveAiBriefing(routineLog.getRoutine()));
    }

    @Transactional
    public DailyRoutineResponse completeAllSteps(Long memberId) {
        RoutineLog routineLog = findTodayRoutineLog(memberId);
        routineLog.getSteps().forEach(step -> step.updateCompleted(true));
        return DailyRoutineResponse.from(routineLog.getRoutine(), routineLog, resolveAiBriefing(routineLog.getRoutine()));
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

    public ArchivedRoutineListResponse getArchivedRoutines(Long memberId, Integer year, String sort) {
        Sort sortOrder = switch (sort) {
            case "NAME" -> Sort.by(Sort.Order.asc("name"), Sort.Order.desc("createdAt"));
            case "STEP_COUNT", "SCORE" -> Sort.unsorted(); // 자바에서 재정렬
            default -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")); // LATEST
        };

        List<Routine> routines;
        if (year != null) {
            LocalDateTime start = LocalDateTime.of(year, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(year, 12, 31, 23, 59, 59);
            routines = routineRepository.findByMemberIdAndStatusAndCreatedAtBetween(
                    memberId, RoutineStatus.ARCHIVED, start, end, sortOrder);
        } else {
            routines = routineRepository.findByMemberIdAndStatusAndCreatedAtAfter(
                    memberId, RoutineStatus.ARCHIVED, LocalDateTime.now().minusYears(3), sortOrder);
        }

        if ("STEP_COUNT".equals(sort)) {
            routines = routines.stream()
                    .sorted(Comparator.comparingInt((Routine r) -> r.getSteps().size())
                            .thenComparing(Routine::getCreatedAt, Comparator.reverseOrder()))
                    .toList();
        }

        Map<Long, Integer> overallScoreByRoutineId = resolveOverallScores(routines);

        if ("SCORE".equals(sort)) {
            routines = routines.stream()
                    .sorted(Comparator
                            .comparing((Routine r) -> overallScoreByRoutineId.get(r.getId()),
                                    Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(Routine::getCreatedAt, Comparator.reverseOrder()))
                    .toList();
        }

        return ArchivedRoutineListResponse.from(routines, overallScoreByRoutineId);
    }

    /**
     * 숏폼 분석에서 온 루틴(sourceAnalysis != null)만 AI 매칭 점수를 구한다.
     * 인벤토리 대체를 반영한 최종 점수(optimizationJson)를 우선 쓰고, 없으면(미최적화/파싱실패)
     * 원본 영상 분석 점수(resultJson)로 폴백한다.
     */
    private Map<Long, Integer> resolveOverallScores(List<Routine> routines) {
        List<Long> analysisIds = routines.stream()
                .map(Routine::getSourceAnalysis)
                .filter(Objects::nonNull)
                .map(ShortformAnalysis::getId)
                .distinct()
                .toList();
        if (analysisIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, ShortformAnalysis> analysisById = shortformAnalysisRepository.findAllById(analysisIds).stream()
                .collect(Collectors.toMap(ShortformAnalysis::getId, Function.identity()));

        Map<Long, Integer> result = new HashMap<>();
        for (Routine routine : routines) {
            ShortformAnalysis source = routine.getSourceAnalysis();
            if (source == null) {
                continue;
            }
            ShortformAnalysis analysis = analysisById.get(source.getId());
            if (analysis == null) {
                continue;
            }
            RoutineOptimizationSnapshot optimization = parseOptimizationSnapshot(analysis, routine.getId());
            Integer overallScore = optimization == null ? null : optimization.overallScore();
            if (overallScore == null) {
                // Member는 LAZY 연관관계라 루틴 개수만큼 추가 쿼리가 날 수 있음(N+1). 지금 규모에선 무시 가능.
                ShortformAnalysisSnapshot snapshot = parseResultSnapshot(analysis, routine.getMember(), routine.getId());
                overallScore = snapshot == null ? null : snapshot.overallScore();
            }
            if (overallScore != null) {
                result.put(routine.getId(), overallScore);
            }
        }
        return result;
    }

    private RoutineOptimizationSnapshot parseOptimizationSnapshot(ShortformAnalysis analysis, Long routineId) {
        String optimizationJson = analysis.getOptimizationJson();
        if (optimizationJson == null || optimizationJson.isBlank()) {
            return null;
        }
        try {
            return shortformAnalysisJsonMapper.read(optimizationJson, RoutineOptimizationSnapshot.class);
        } catch (CustomException exception) {
            log.warn("최적화 결과 파싱 실패: routineId={}", routineId, exception);
            return null;
        }
    }

    private ShortformAnalysisSnapshot parseResultSnapshot(ShortformAnalysis analysis, Member member, Long routineId) {
        String resultJson = analysis.getResultJson();
        if (resultJson == null || resultJson.isBlank()) {
            return null;
        }
        try {
            return shortformAnalysisSnapshotNormalizer.normalize(
                    shortformAnalysisJsonMapper.read(resultJson, ShortformAnalysisSnapshot.class),
                    member.getNickname(),
                    member.getSkinType() == null ? null : member.getSkinType().getLabel());
        } catch (CustomException exception) {
            log.warn("원본 분석 파싱 실패: routineId={}", routineId, exception);
            return null;
        }
    }

    public RoutineDetailResponse getRoutineDetail(Long memberId, Long routineId) {
        Routine routine = routineRepository.findByIdAndMemberId(routineId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTINE_NOT_FOUND));
        AiBriefing aiBriefing = resolveAiBriefing(routine);
        Map<Integer, String> safetyEvaluationByOrder = resolveSafetyEvaluations(memberId, routine.getSteps());
        return RoutineDetailResponse.from(routine, aiBriefing, safetyEvaluationByOrder);
    }

    /**
     * 6.10 AI 브리핑. sourceAnalysis가 없으면(수동생성/AI자동생성 루틴) 전체 null.
     * 있으면 resultJson(원본 영상 분석)과 optimizationJson(인벤토리 최적화 결과)을 각각 독립적으로
     * 파싱해 합친다 - 한쪽이 blank/파싱실패여도 다른 쪽 필드는 정상 표시되고 전체 API는 죽지 않는다.
     */
    private AiBriefing resolveAiBriefing(Routine routine) {
        ShortformAnalysis source = routine.getSourceAnalysis();
        if (source == null) {
            return null;
        }

        ShortformAnalysisSnapshot snapshot = parseResultSnapshot(source, routine.getMember(), routine.getId());

        Integer overallScore = null;
        List<String> highlights = null;
        String optimizationSummary = null;
        RoutineOptimizationSnapshot optimization = parseOptimizationSnapshot(source, routine.getId());
        if (optimization != null) {
            overallScore = optimization.overallScore();
            highlights = optimization.highlights();
            optimizationSummary = optimization.summary();
        }

        // 폴백: optimizationJson 쪽 값이 없으면 원본(resultJson) 값 사용
        if (overallScore == null && snapshot != null) {
            overallScore = snapshot.overallScore();
        }
        if ((highlights == null || highlights.isEmpty()) && snapshot != null) {
            highlights = snapshot.highlights();
        }
        if ((optimizationSummary == null || optimizationSummary.isBlank()) && snapshot != null) {
            optimizationSummary = snapshot.summary();
        }
        if (highlights == null) {
            highlights = List.of();
        }

        return new AiBriefing(
                snapshot == null ? null : snapshot.title(),
                snapshot == null ? null : snapshot.tag(),
                overallScore,
                highlights,
                snapshot == null ? null : snapshot.coreGoal(),
                snapshot == null ? null : snapshot.synergyCombo(),
                optimizationSummary);
    }

    /**
     * 6.10 스텝별 AI 안전성 평가. product가 있는 스텝만 4.4(SkinAnalysisService)를 병렬 호출해
     * aiAnalysis.reasons의 첫 번째 근거를 담는다. 실패한 스텝은 null 처리하고 나머지는 정상 진행한다.
     */
    private Map<Integer, String> resolveSafetyEvaluations(Long memberId, List<RoutineStep> steps) {
        Map<Integer, CompletableFuture<String>> futuresByOrder = new LinkedHashMap<>();
        for (RoutineStep step : steps) {
            Product product = step.getProduct();
            if (product == null) {
                continue;
            }
            Long productId = product.getId();
            int order = step.getOrder();
            futuresByOrder.put(order, CompletableFuture.supplyAsync(() -> {
                try {
                    SkinAnalysisResponse response = skinAnalysisService.analyze(memberId, productId);
                    List<AnalysisReason> reasons = response.aiAnalysis() == null
                            ? List.of() : response.aiAnalysis().reasons();
                    return (reasons == null || reasons.isEmpty()) ? null : reasons.get(0).reason();
                } catch (RuntimeException exception) {
                    log.warn("스텝 안전성 평가 실패: order={}, productId={}", order, productId, exception);
                    return null;
                }
            }));
        }

        CompletableFuture.allOf(futuresByOrder.values().toArray(CompletableFuture[]::new)).join();

        Map<Integer, String> result = new HashMap<>();
        futuresByOrder.forEach((order, future) -> result.put(order, future.join()));
        return result;
    }

    @Transactional
    public RoutineDeleteResponse deleteRoutine(Long memberId, Long routineId) {
        Routine routine = routineRepository.findByIdAndMemberId(routineId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTINE_NOT_FOUND));

        List<RoutineLog> logs = routineLogRepository.findByRoutineId(routineId);
        routineLogRepository.deleteAll(logs);
        routineRepository.delete(routine);

        return new RoutineDeleteResponse(routineId);
    }

    @Transactional
    public DailyRoutineResponse applyToday(Long memberId, Long routineId) {
        Routine routine = routineRepository.findByIdAndMemberId(routineId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTINE_NOT_FOUND));

        RoutineLog routineLog = applyAsTodayActive(memberId, routine);
        return DailyRoutineResponse.from(routine, routineLog, resolveAiBriefing(routine));
    }

    public RoutineGenerationResponse generateRoutine(Long memberId, RoutineType routineType) {
        List<Inventory> inventories = inventoryRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId);

        Map<ProductCategory, Inventory> bestInventoryByCategory = new EnumMap<>(ProductCategory.class);
        Map<ProductCategory, Integer> bestScoreByCategory = new EnumMap<>(ProductCategory.class);

        for (Inventory inventory : inventories) {
            ProductCategory category = inventory.getProduct().getCategory();
            if (category == null) {
                continue;
            }
            SkinAnalysisResponse analysis = skinAnalysisService.analyze(memberId, inventory.getProduct().getId());
            Integer bestSoFar = bestScoreByCategory.get(category);
            if (bestSoFar == null || analysis.matchScore() > bestSoFar) {
                bestScoreByCategory.put(category, analysis.matchScore());
                bestInventoryByCategory.put(category, inventory);
            }
        }

        List<GeneratedStep> steps = new ArrayList<>();
        int order = 1;
        for (ProductCategory category : ProductCategory.values()) {
            Inventory inventory = bestInventoryByCategory.get(category);
            if (inventory == null) {
                continue;
            }
            steps.add(new GeneratedStep(
                    order++, inventory.getId(), category,
                    inventory.getProduct().getName(), bestScoreByCategory.get(category)));
        }

        List<String> warnings = collectConflictWarnings(steps);
        String suggestedName = routineType == RoutineType.DAY ? "AI 추천 데이 루틴" : "AI 추천 나이트 루틴";

        return new RoutineGenerationResponse(suggestedName, routineType, steps, warnings);
    }

    @Transactional
    public RoutineCreateResponse createRoutine(Long memberId, RoutineCreateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        Routine routine = new Routine(
                member, null, request.name(), request.routineType(), RoutineStatus.ARCHIVED, request.saveType());

        for (RoutineStepCreateRequest stepRequest : request.steps()) {
            Inventory inventory = inventoryRepository.findByIdAndMemberId(stepRequest.inventoryId(), memberId)
                    .orElseThrow(() -> new CustomException(ErrorCode.INVENTORY_NOT_FOUND));
            Product product = inventory.getProduct();
            routine.addStep(new RoutineStep(
                    routine, product, inventory, stepRequest.order(),
                    product.getName(), product.getBrand(),
                    product.getCategory() != null ? product.getCategory().name() : null,
                    product.getImageUrl(), null));
        }

        Routine saved = routineRepository.saveAndFlush(routine);

        if (request.saveType() == RoutineSaveType.TODAY) {
            applyAsTodayActive(memberId, saved);
        }

        return new RoutineCreateResponse(saved.getId(), saved.getName(), saved.getRoutineType(), saved.getStatus());
    }

    private RoutineLog applyAsTodayActive(Long memberId, Routine routine) {
        routineRepository.findByMemberIdAndStatusAndRoutineType(memberId, RoutineStatus.ACTIVE, routine.getRoutineType())
                .filter(existing -> !existing.getId().equals(routine.getId()))
                .ifPresent(Routine::archive);
        routine.activate();

        LocalDate today = LocalDate.now();
        return routineLogRepository
                .findByMemberIdAndLogDateAndRoutineId(memberId, today, routine.getId())
                .orElseGet(() -> createTodayLog(routine, today));
    }

    private List<String> collectConflictWarnings(List<GeneratedStep> steps) {
        List<String> warnings = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();

        for (GeneratedStep base : steps) {
            List<Long> otherProductIds = steps.stream()
                    .filter(step -> !step.equals(base))
                    .map(GeneratedStep::inventoryId)
                    .map(inventoryId -> inventoryRepository.findById(inventoryId).orElseThrow().getProduct().getId())
                    .toList();
            if (otherProductIds.isEmpty()) {
                continue;
            }

            Long baseProductId = inventoryRepository.findById(base.inventoryId()).orElseThrow().getProduct().getId();
            ProductCompatibilityResponse result = productCompatibilityService.compare(
                    new ProductCompatibilityRequest(baseProductId, otherProductIds));

            for (CompatibilityResult compatibilityResult : result.results()) {
                if (compatibilityResult.interactionType() != InteractionType.CONFLICT) {
                    continue;
                }
                String pairKey = pairKey(baseProductId, compatibilityResult.compareProductId());
                if (seenPairs.add(pairKey)) {
                    warnings.add("%s와(과) %s: %s".formatted(
                            base.productName(), compatibilityResult.compareProductName(), compatibilityResult.description()));
                }
            }
        }
        return warnings;
    }

    private String pairKey(Long a, Long b) {
        return a < b ? a + "-" + b : b + "-" + a;
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