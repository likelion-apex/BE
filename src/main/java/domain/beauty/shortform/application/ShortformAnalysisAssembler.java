package domain.beauty.shortform.application;

import domain.beauty.domain.BeautyRoutineAnalysis;
import domain.beauty.domain.BeautyRoutineAnalysis.IdentificationLevel;
import domain.beauty.shortform.application.ShortformAnalysisStateService.InventoryFact;
import domain.beauty.shortform.application.ShortformAnalysisStateService.JobContext;
import domain.beauty.shortform.client.RoutinePersonalizationInput;
import domain.beauty.shortform.client.RoutinePersonalizationResult;
import domain.beauty.shortform.client.RoutinePersonalizationResult.Response;
import domain.beauty.shortform.config.OpenAiRoutineProperties;
import domain.beauty.shortform.domain.OptimizationStatus;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot.OptimizedStep;
import domain.beauty.shortform.domain.SafetyLevel;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.AiMetadata;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.IngredientDetail;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ReasonCard;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ReasonTone;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.StepResult;
import domain.beauty.shortform.domain.VideoRoutineExtraction;
import domain.cosmetic.cache.RegulationInfoCache;
import domain.cosmetic.client.RegulationInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ShortformAnalysisAssembler {

    public static final String DISCLAIMER =
            "AI가 영상·제품명·회원 프로필을 바탕으로 생성한 참고 정보입니다. 실제 전성분 라벨과 피부 반응을 확인하고 이상 반응 시 사용을 중단해 주세요.";

    private final RegulationInfoCache regulationInfoCache;
    private final OpenAiRoutineProperties openAiProperties;

    public ShortformAnalysisAssembler(
            RegulationInfoCache regulationInfoCache,
            OpenAiRoutineProperties openAiProperties
    ) {
        this.regulationInfoCache = regulationInfoCache;
        this.openAiProperties = openAiProperties;
    }

    public RoutinePersonalizationInput toInput(
            JobContext context,
            BeautyRoutineAnalysis extraction,
            List<MatchedVideoStep> matchedSteps
    ) {
        return new RoutinePersonalizationInput(
                new RoutinePersonalizationInput.MemberProfile(
                        context.nickname(), context.skinType(), context.skinConcerns()),
                new RoutinePersonalizationInput.VideoContext(
                        context.videoId(), extraction.summary(), safe(extraction.warnings())),
                matchedSteps.stream().map(step -> new RoutinePersonalizationInput.VideoStep(
                        step.source().order(),
                        step.source().category(),
                        step.source().brand(),
                        step.source().productName(),
                        step.source().purpose(),
                        step.source().evidenceSummary(),
                        step.source().confidence(),
                        step.productId()
                )).toList(),
                context.inventory().stream().map(item -> new RoutinePersonalizationInput.InventoryProduct(
                        item.inventoryId(),
                        item.productId(),
                        item.category(),
                        item.brand(),
                        item.productName()
                )).toList()
        );
    }

    public AssembledResult assemble(
            JobContext context,
            List<MatchedVideoStep> matchedSteps,
            Response aiResponse,
            VideoRoutineExtraction extraction
    ) {
        RoutinePersonalizationResult ai = aiResponse.analysis();
        Map<Integer, RoutinePersonalizationResult.StepAnalysis> aiSteps = indexSteps(ai.steps());
        Map<Integer, RoutinePersonalizationResult.InventoryRecommendation> recommendations =
                indexRecommendations(ai.inventoryRecommendations());

        List<StepResult> steps = matchedSteps.stream()
                .map(matched -> toStepResult(matched, aiSteps.get(matched.source().order())))
                .toList();

        ShortformAnalysisSnapshot snapshot = new ShortformAnalysisSnapshot(
                "1.0",
                context.videoId(),
                context.youtubeUrl(),
                textOr(ai.title(), "나를 위한 스킨케어 루틴"),
                textOr(ai.tag(), context.skinType() + " 맞춤"),
                clamp(ai.overallScore()),
                safe(ai.highlights()),
                textOr(ai.coreGoal(), "피부 컨디션에 맞춘 단계별 관리"),
                textOr(ai.synergyCombo(), "영상 속 제품 조합"),
                textOr(ai.summary(), "영상 속 스킨케어 단계를 피부 프로필에 맞춰 분석했습니다."),
                mergeWarnings(ai.warnings()),
                DISCLAIMER,
                steps,
                new AiMetadata(
                        extraction.getModel(),
                        extraction.getPromptVersion(),
                        extraction.getInputTokens(),
                        extraction.getOutputTokens(),
                        aiResponse.model(),
                        openAiProperties.getRoutinePromptVersion(),
                        aiResponse.inputTokens(),
                        aiResponse.outputTokens()
                )
        );

        return new AssembledResult(snapshot, optimize(context.inventory(), matchedSteps, recommendations));
    }

    private StepResult toStepResult(
            MatchedVideoStep matched,
            RoutinePersonalizationResult.StepAnalysis aiStep
    ) {
        BeautyRoutineAnalysis.Step source = matched.source();
        boolean exactProduct = source.identificationLevel() == IdentificationLevel.EXACT_PRODUCT;
        RoutinePersonalizationResult.StepAnalysis normalized = aiStep == null ? fallbackStep(source.order()) : aiStep;
        SafetyLevel safetyLevel = exactProduct && normalized.safetyLevel() != null
                ? normalized.safetyLevel()
                : SafetyLevel.UNKNOWN;
        List<IngredientDetail> ingredients = exactProduct
                ? safe(normalized.ingredients()).stream().map(this::toIngredient).toList()
                : List.of();
        List<ReasonCard> reasons = safe(normalized.reasons()).stream()
                .map(reason -> new ReasonCard(
                        reason.tone() == null ? ReasonTone.NEUTRAL : reason.tone(),
                        textOr(reason.title(), "AI 분석"),
                        textOr(reason.description(), "확인 가능한 근거가 부족합니다."),
                        textOr(reason.evidenceSource(), "AI_ESTIMATED")
                ))
                .toList();
        if (reasons.isEmpty()) {
            reasons = List.of(new ReasonCard(
                    ReasonTone.NEUTRAL,
                    "성분 확인 필요",
                    "제품 라벨의 전성분을 확인한 뒤 피부 반응을 살펴보세요.",
                    "VIDEO_EVIDENCE"
            ));
        }

        return new StepResult(
                source.order(),
                source.order(),
                source.startTime(),
                source.endTime(),
                source.category(),
                source.brand(),
                source.productName() == null ? source.category() : source.productName(),
                matched.imageUrl(),
                matched.productId(),
                source.confidence(),
                source.evidenceSummary(),
                clamp(normalized.matchScore()),
                textOr(normalized.matchSummary(), "피부 프로필과의 궁합을 확인할 정보가 부족합니다."),
                safetyLevel,
                exactProduct ? textOr(normalized.safetyTitle(), "AI 안전도 참고") : "제품 식별 정보 부족",
                exactProduct
                        ? textOr(normalized.safetySummary(), "실제 전성분과 패치 테스트를 함께 확인해 주세요.")
                        : "영상에서 정확한 제품을 확인하지 못해 안전도를 판단하지 않았습니다.",
                reasons,
                exactProduct ? Math.max(normalized.estimatedIngredientCount(), ingredients.size()) : 0,
                ingredients
        );
    }

    private IngredientDetail toIngredient(RoutinePersonalizationResult.Ingredient ingredient) {
        Optional<RegulationInfo> regulation = regulationInfoCache.find(ingredient.name());
        return new IngredientDetail(
                ingredient.name(),
                safe(ingredient.purposes()),
                "AI_ESTIMATED",
                regulation.isPresent(),
                regulation.map(this::regulationSummary).orElse(null)
        );
    }

    private String regulationSummary(RegulationInfo info) {
        List<String> messages = new ArrayList<>();
        if (info.prohibitedCountries() != null && !info.prohibitedCountries().isEmpty()) {
            messages.add("금지 국가: " + String.join(", ", info.prohibitedCountries()));
        }
        if (info.restrictedCountries() != null && !info.restrictedCountries().isEmpty()) {
            messages.add("제한 국가: " + String.join(", ", info.restrictedCountries()));
        }
        return messages.isEmpty() ? "식약처 규제 원료 목록에서 명칭이 확인되었습니다." : String.join(" / ", messages);
    }

    private RoutineOptimizationSnapshot optimize(
            List<InventoryFact> inventory,
            List<MatchedVideoStep> matchedSteps,
            Map<Integer, RoutinePersonalizationResult.InventoryRecommendation> recommendations
    ) {
        Map<Long, InventoryFact> inventoryById = new HashMap<>();
        inventory.forEach(item -> inventoryById.put(item.inventoryId(), item));
        List<OptimizedStep> steps = new ArrayList<>();
        int newProductCount = 0;
        int compatibleCount = 0;
        int replacedCount = 0;
        int missingCount = 0;

        for (MatchedVideoStep matched : matchedSteps) {
            BeautyRoutineAnalysis.Step source = matched.source();
            RoutinePersonalizationResult.InventoryRecommendation recommendation = recommendations.get(source.order());
            InventoryFact selected = recommendation == null ? null : inventoryById.get(recommendation.inventoryId());
            OptimizationStatus status;
            Long productId;
            String category;
            String productName;
            String brand;
            String imageUrl;
            String reason;

            if (selected != null) {
                boolean sameProduct = matched.productId() != null && matched.productId().equals(selected.productId());
                status = sameProduct ? OptimizationStatus.COMPATIBLE : OptimizationStatus.REPLACED;
                compatibleCount += sameProduct ? 1 : 0;
                replacedCount += sameProduct ? 0 : 1;
                productId = selected.productId();
                category = selected.category();
                productName = selected.productName();
                brand = selected.brand();
                imageUrl = selected.imageUrl();
                reason = textOr(recommendation.reason(), sameProduct ? "영상 제품과 같은 보유 제품입니다." : "같은 단계에 사용할 보유 제품입니다.");
            } else {
                boolean exact = source.identificationLevel() == IdentificationLevel.EXACT_PRODUCT;
                status = exact ? OptimizationStatus.VIDEO_PRODUCT : OptimizationStatus.NO_INVENTORY_MATCH;
                missingCount++;
                productId = matched.productId();
                category = source.category();
                productName = source.productName() == null ? source.category() : source.productName();
                brand = source.brand();
                imageUrl = matched.imageUrl();
                reason = recommendation == null
                        ? "인벤토리에서 같은 역할의 제품을 찾지 못했습니다."
                        : textOr(recommendation.reason(), "인벤토리 대체품이 없습니다.");
            }

            boolean alreadyOwned = matched.productId() != null && inventory.stream()
                    .anyMatch(item -> Objects.equals(item.productId(), matched.productId()));
            if (source.identificationLevel() == IdentificationLevel.EXACT_PRODUCT && !alreadyOwned) {
                newProductCount++;
            }

            steps.add(new OptimizedStep(
                    source.order(),
                    source.order(),
                    status,
                    selected == null ? null : selected.inventoryId(),
                    productId,
                    category,
                    productName,
                    brand,
                    imageUrl,
                    reason
            ));
        }

        String summary = replacedCount > 0
                ? "영상 속 제품 중 %d개를 인벤토리의 같은 역할 제품으로 교체했습니다.".formatted(replacedCount)
                : "영상 속 루틴과 현재 인벤토리의 조합을 확인했습니다.";
        return new RoutineOptimizationSnapshot(
                newProductCount, compatibleCount, replacedCount, missingCount, summary, List.copyOf(steps));
    }

    private Map<Integer, RoutinePersonalizationResult.StepAnalysis> indexSteps(
            List<RoutinePersonalizationResult.StepAnalysis> steps
    ) {
        Map<Integer, RoutinePersonalizationResult.StepAnalysis> indexed = new LinkedHashMap<>();
        safe(steps).forEach(step -> indexed.putIfAbsent(step.order(), step));
        return indexed;
    }

    private Map<Integer, RoutinePersonalizationResult.InventoryRecommendation> indexRecommendations(
            List<RoutinePersonalizationResult.InventoryRecommendation> recommendations
    ) {
        Map<Integer, RoutinePersonalizationResult.InventoryRecommendation> indexed = new LinkedHashMap<>();
        safe(recommendations).forEach(item -> indexed.putIfAbsent(item.order(), item));
        return indexed;
    }

    private RoutinePersonalizationResult.StepAnalysis fallbackStep(int order) {
        return new RoutinePersonalizationResult.StepAnalysis(
                order,
                50,
                "추가 확인이 필요한 단계입니다.",
                SafetyLevel.UNKNOWN,
                "성분 확인 필요",
                "제품 라벨과 피부 반응을 직접 확인해 주세요.",
                List.of(),
                0,
                List.of()
        );
    }

    private List<String> mergeWarnings(List<String> aiWarnings) {
        List<String> warnings = new ArrayList<>(safe(aiWarnings));
        warnings.add("제품 성분은 AI 추정치일 수 있으며 실제 라벨이 우선합니다.");
        return warnings.stream().filter(Objects::nonNull).filter(value -> !value.isBlank()).distinct().toList();
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record AssembledResult(
            ShortformAnalysisSnapshot analysis,
            RoutineOptimizationSnapshot optimization
    ) {
    }
}
