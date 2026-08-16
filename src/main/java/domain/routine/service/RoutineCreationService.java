package domain.routine.service;

import domain.beauty.shortform.domain.RoutineOptimizationSnapshot;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot.OptimizedStep;
import domain.beauty.shortform.domain.RoutineSaveType;
import domain.beauty.shortform.domain.ShortformAnalysis;
import domain.beauty.shortform.domain.ShortformAnalysisRepository;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot;
import domain.inventory.Inventory;
import domain.inventory.InventoryRepository;
import domain.inventory.Product;
import domain.inventory.ProductRepository;
import domain.member.Member;
import domain.routine.domain.*;
import domain.routine.repository.RoutineLogRepository;
import domain.routine.repository.RoutineRepository;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoutineCreationService {

    private final RoutineRepository routineRepository;
    private final RoutineLogRepository routineLogRepository;
    private final ShortformAnalysisRepository analysisRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    public RoutineCreationService(
            RoutineRepository routineRepository,
            RoutineLogRepository routineLogRepository,
            ShortformAnalysisRepository analysisRepository,
            InventoryRepository inventoryRepository,
            ProductRepository productRepository
    ) {
        this.routineRepository = routineRepository;
        this.routineLogRepository = routineLogRepository;
        this.analysisRepository = analysisRepository;
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public RoutineApplyResult create(
            Long memberId,
            Long analysisId,
            RoutineSaveType saveType,
            RoutineType routineType,
            ShortformAnalysisSnapshot analysisSnapshot,
            RoutineOptimizationSnapshot optimization
    ) {
        Routine existing = routineRepository
                .findByMemberIdAndSourceAnalysisIdAndSaveTypeAndRoutineType(
                        memberId, analysisId, saveType, routineType)
                .orElse(null);
        if (existing != null) {
            return RoutineApplyResult.from(existing, true);
        }

        ShortformAnalysis source = analysisRepository.findByIdAndMemberId(analysisId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.SHORTFORM_ANALYSIS_NOT_FOUND));
        Member member = source.getMember();

        if (saveType == RoutineSaveType.TODAY
                && routineRepository.existsByMemberIdAndStatusAndRoutineType(
                        memberId, RoutineStatus.ACTIVE, routineType)) {
            throw new CustomException(ErrorCode.ROUTINE_TODAY_CONFLICT);
        }

        Routine routine = new Routine(
                member,
                source,
                analysisSnapshot.title(),
                routineType,
                saveType == RoutineSaveType.TODAY ? RoutineStatus.ACTIVE : RoutineStatus.ARCHIVED,
                saveType
        );
        for (OptimizedStep step : optimization.steps()) {
            Inventory inventory = step.inventoryId() == null
                    ? null
                    : inventoryRepository.findByIdAndMemberId(step.inventoryId(), memberId).orElse(null);
            Product product = inventory != null
                    ? inventory.getProduct()
                    : step.productId() == null ? null : productRepository.findById(step.productId()).orElse(null);
            routine.addStep(new RoutineStep(
                    routine,
                    product,
                    inventory,
                    step.order(),
                    step.productName(),
                    step.brand(),
                    step.category(),
                    step.imageUrl(),
                    step.reason()
            ));
        }
        Routine saved = routineRepository.saveAndFlush(routine);

        if (saveType == RoutineSaveType.TODAY) {
            RoutineLog log = new RoutineLog(saved, member, LocalDate.now());
            saved.getSteps().forEach(step -> log.addStep(new RoutineLogStep(log, step.getId(), step.getOrder())));
            routineLogRepository.save(log);
        }
        return RoutineApplyResult.from(saved, false);
    }

    public record RoutineApplyResult(
            Long routineId,
            RoutineSaveType saveType,
            RoutineType routineType,
            RoutineStatus status,
            boolean reused
    ) {
        static RoutineApplyResult from(Routine routine, boolean reused) {
            return new RoutineApplyResult(
                    routine.getId(), routine.getSaveType(), routine.getRoutineType(), routine.getStatus(), reused);
        }
    }
}
