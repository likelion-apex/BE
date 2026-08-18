package domain.beauty.shortform.application;

import domain.beauty.shortform.application.ShortformAnalysisStateService.AnalysisProfile;
import domain.beauty.shortform.application.ShortformAnalysisStateService.InventoryFact;
import domain.beauty.shortform.client.OpenAiOptimizationReasonClient;
import domain.beauty.shortform.client.OptimizationReasonInput;
import domain.beauty.shortform.client.OptimizationReasonResult;
import domain.beauty.shortform.domain.IngredientDataStatus;
import domain.beauty.shortform.domain.OptimizationStatus;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot.OptimizedStep;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.StepResult;
import global.exception.CustomException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OptimizationReasonRefresher {

    public static final String CURRENT_VERSION = "3.3";

    private final InventoryProductEvidenceService evidenceService;
    private final OpenAiOptimizationReasonClient reasonClient;
    private final OptimizationReasonComposer reasonComposer;

    public OptimizationReasonRefresher(
            InventoryProductEvidenceService evidenceService,
            OpenAiOptimizationReasonClient reasonClient,
            OptimizationReasonComposer reasonComposer
    ) {
        this.evidenceService = evidenceService;
        this.reasonClient = reasonClient;
        this.reasonComposer = reasonComposer;
    }

    public RoutineOptimizationSnapshot refresh(
            AnalysisProfile profile,
            ShortformAnalysisSnapshot analysis,
            RoutineOptimizationSnapshot optimization
    ) {
        Map<Integer, StepResult> sourceByOrder = new HashMap<>();
        safe(analysis.steps()).forEach(step -> sourceByOrder.putIfAbsent(step.order(), step));

        List<InventoryFact> selectedProducts = safe(optimization.steps()).stream()
                .filter(step -> step.status() == OptimizationStatus.REPLACED)
                .filter(step -> step.productId() != null)
                .map(step -> new InventoryFact(
                        step.inventoryId(),
                        step.productId(),
                        step.productName(),
                        step.brand(),
                        step.category(),
                        step.imageUrl()))
                .toList();
        Map<Long, InventoryProductEvidence> evidenceByProduct;
        try {
            evidenceByProduct = evidenceService.enrich(selectedProducts);
        } catch (CustomException exception) {
            log.warn("기존 최적화 제품 성분 보강 실패로 저장된 제품 정보만 사용합니다: reason={}",
                    exception.getErrorCode());
            evidenceByProduct = Map.of();
        }
        Map<Long, InventoryProductEvidence> availableEvidence = evidenceByProduct;

        OptimizationReasonInput input = new OptimizationReasonInput(
                new OptimizationReasonInput.MemberProfile(profile.skinType(), profile.skinConcerns()),
                safe(optimization.steps()).stream()
                        .map(step -> toInput(step, sourceByOrder.get(step.order()), availableEvidence))
                        .filter(Objects::nonNull)
                        .toList());

        Map<Integer, String> aiReasons = new LinkedHashMap<>();
        if (!input.steps().isEmpty()) {
            try {
                OptimizationReasonResult.Response response = reasonClient.generate(input);
                safe(response.result() == null ? null : response.result().steps()).forEach(item ->
                        aiReasons.putIfAbsent(item.order(), item.reason()));
                log.info("기존 최적화 맞춤 이유 갱신 완료: steps={}, model={}, inputTokens={}, outputTokens={}",
                        input.steps().size(), response.model(), response.inputTokens(), response.outputTokens());
            } catch (CustomException exception) {
                log.warn("기존 최적화 맞춤 이유 AI 갱신 실패로 서버 문구를 사용합니다: reason={}",
                        exception.getErrorCode());
            }
        }

        List<OptimizedStep> refreshed = new ArrayList<>();
        for (OptimizedStep step : safe(optimization.steps())) {
            StepResult source = sourceByOrder.get(step.order());
            if (source == null) {
                refreshed.add(step);
                continue;
            }
            InventoryProductEvidence evidence = step.productId() == null
                    ? null
                    : availableEvidence.get(step.productId());
            String reason = reasonComposer.forStoredAnalysis(
                    profile, source, step, evidence, aiReasons.get(step.order()));
            refreshed.add(withReason(step, reason));
        }
        return new RoutineOptimizationSnapshot(
                optimization.newProductCount(),
                optimization.replacedCount(),
                optimization.missingCount(),
                optimization.summary(),
                List.copyOf(refreshed));
    }

    private OptimizationReasonInput.Step toInput(
            OptimizedStep optimized,
            StepResult source,
            Map<Long, InventoryProductEvidence> evidenceByProduct
    ) {
        if (source == null) {
            return null;
        }
        OptimizationReasonInput.Product video = new OptimizationReasonInput.Product(
                source.category(),
                textOr(source.displayProductName(), textOr(source.productName(), source.category())),
                textOr(source.matchSummary(), source.evidenceSummary()),
                safe(source.keyBenefits()),
                source.ingredientDataStatus(),
                safe(source.ingredients()).stream()
                        .map(item -> new OptimizationReasonInput.Ingredient(
                                item.name(), safe(item.purposes()), safe(item.skinBenefits())))
                        .toList());
        OptimizationReasonInput.Product inventory = null;
        if (optimized.status() == OptimizationStatus.REPLACED) {
            InventoryProductEvidence evidence = optimized.productId() == null
                    ? InventoryProductEvidence.unavailable()
                    : evidenceByProduct.getOrDefault(
                            optimized.productId(), InventoryProductEvidence.unavailable());
            inventory = new OptimizationReasonInput.Product(
                    optimized.category(),
                    optimized.productName(),
                    null,
                    List.of(),
                    evidence.isAvailable() ? IngredientDataStatus.AVAILABLE : IngredientDataStatus.UNAVAILABLE,
                    safe(evidence.ingredients()).stream()
                            .map(item -> new OptimizationReasonInput.Ingredient(
                                    item.name(), safe(item.purposes()), safe(item.skinBenefits())))
                            .toList());
        }
        return new OptimizationReasonInput.Step(optimized.order(), optimized.status(), video, inventory);
    }

    private OptimizedStep withReason(OptimizedStep step, String reason) {
        return new OptimizedStep(
                step.sourceResultId(),
                step.order(),
                step.status(),
                step.inventoryId(),
                step.productId(),
                step.category(),
                step.productName(),
                step.replaceName(),
                step.brand(),
                step.imageUrl(),
                reason);
    }

    private String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
