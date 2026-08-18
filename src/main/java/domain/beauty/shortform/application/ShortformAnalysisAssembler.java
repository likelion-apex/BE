package domain.beauty.shortform.application;

import domain.beauty.domain.BeautyRoutineAnalysis;
import domain.beauty.domain.BeautyRoutineAnalysis.IdentificationLevel;
import domain.beauty.shortform.application.ShortformAnalysisStateService.InventoryFact;
import domain.beauty.shortform.application.ShortformAnalysisStateService.JobContext;
import domain.beauty.shortform.client.RoutinePersonalizationInput;
import domain.beauty.shortform.client.RoutinePersonalizationResult;
import domain.beauty.shortform.client.RoutinePersonalizationResult.Response;
import domain.beauty.shortform.client.ProductEnrichmentResult;
import domain.beauty.shortform.config.OpenAiRoutineProperties;
import domain.beauty.shortform.domain.AssessmentCategory;
import domain.beauty.shortform.domain.IngredientDataStatus;
import domain.beauty.shortform.domain.IngredientRiskLevel;
import domain.beauty.shortform.domain.IngredientVerificationStatus;
import domain.beauty.shortform.domain.OptimizationStatus;
import domain.beauty.shortform.domain.ProductResolutionStatus;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot.OptimizedStep;
import domain.beauty.shortform.domain.SafetyLevel;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.AiMetadata;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.IngredientDetail;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.IngredientSource;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.IngredientStats;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ReasonCard;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ReasonTone;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.ScoreBreakdown;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot.StepResult;
import domain.beauty.shortform.domain.VideoRoutineExtraction;
import domain.beauty.shortform.application.ShortformProductEnrichmentService.BatchResult;
import domain.cosmetic.cache.RegulationInfoCache;
import domain.cosmetic.client.RegulationInfo;
import domain.inventory.ProductCategory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ShortformAnalysisAssembler {

    public static final String DISCLAIMER =
            "성분과 피부 반응에는 개인차가 있습니다. 실제 제품 라벨을 확인하고 이상 반응이 있으면 사용을 중단해 주세요.";

    private static final Set<String> INTERNAL_COPY_MARKERS = Set.of(
            "AI가", "AI는", "AI의", "AI 분석", "추정", "식별", "대표 처방", "서버", "모델", "확신도",
            "NORMALIZED", "ESTIMATED");
    private static final Set<String> OWNERSHIP_COPY_MARKERS = Set.of(
            "님", "내 루틴", "내 피부", "나의", "저의", "사용자", "회원", "고객");

    private final RegulationInfoCache regulationInfoCache;
    private final OpenAiRoutineProperties openAiProperties;
    private final ShortformProductCategoryResolver categoryResolver;

    public ShortformAnalysisAssembler(
            RegulationInfoCache regulationInfoCache,
            OpenAiRoutineProperties openAiProperties,
            ShortformProductCategoryResolver categoryResolver
    ) {
        this.regulationInfoCache = regulationInfoCache;
        this.openAiProperties = openAiProperties;
        this.categoryResolver = categoryResolver;
    }

    public RoutinePersonalizationInput toInput(
            JobContext context,
            BeautyRoutineAnalysis extraction,
            List<MatchedVideoStep> matchedSteps
    ) {
        return new RoutinePersonalizationInput(
                new RoutinePersonalizationInput.MemberProfile(
                        context.skinType(), context.skinConcerns()),
                new RoutinePersonalizationInput.VideoContext(
                        context.videoId(), extraction.summary(), safe(extraction.warnings())),
                matchedSteps.stream().map(step -> new RoutinePersonalizationInput.VideoStep(
                        step.source().order(),
                        step.source().category(),
                        step.productCategory().name(),
                        step.source().brand(),
                        step.source().productName(),
                        step.displayBrand(),
                        step.displayProductName(),
                        step.source().purpose(),
                        step.source().evidenceSummary(),
                        step.source().confidence(),
                        step.productId(),
                        step.ingredientDataStatus(),
                        safe(step.enrichment().ingredients()).stream()
                                .map(ingredient -> new RoutinePersonalizationInput.Ingredient(
                                        ingredient.order(),
                                        ingredient.name(),
                                        safe(ingredient.purposes()),
                                        safe(ingredient.skinBenefits()),
                                        ingredient.riskScore(),
                                        ingredient.caution20(),
                                        ingredient.allergen()
                                ))
                                .toList()
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
            VideoRoutineExtraction extraction,
            BatchResult enrichment
    ) {
        RoutinePersonalizationResult ai = aiResponse.analysis();
        Map<Integer, RoutinePersonalizationResult.StepAnalysis> aiSteps = indexSteps(ai.steps());
        Map<Integer, RoutinePersonalizationResult.InventoryRecommendation> recommendations =
                indexRecommendations(ai.inventoryRecommendations());

        List<StepResult> steps = matchedSteps.stream()
                .map(matched -> toStepResult(matched, aiSteps.get(matched.source().order())))
                .toList();
        int overallScore = steps.isEmpty()
                ? 0
                : (int) Math.round(steps.stream().mapToInt(StepResult::matchScore).average().orElse(0));
        String coreGoal = userCopy(ai.coreGoal(), "피부 컨디션에 맞춘 단계별 관리");

        ShortformAnalysisSnapshot snapshot = new ShortformAnalysisSnapshot(
                "3.0",
                context.videoId(),
                context.youtubeUrl(),
                routineTitle(ai.title(), coreGoal, steps),
                userCopy(ai.tag(), context.skinType() + " 맞춤"),
                overallScore,
                safe(ai.highlights()).stream()
                        .map(value -> userCopy(value, "피부 상태에 맞춘 단계별 관리"))
                        .distinct()
                        .limit(2)
                        .toList(),
                coreGoal,
                userCopy(ai.synergyCombo(), "영상 속 제품 조합"),
                routineSummary(ai.summary(), steps),
                mergeWarnings(ai.warnings()),
                DISCLAIMER,
                steps,
                new AiMetadata(
                        extraction.getModel(),
                        extraction.getPromptVersion(),
                        extraction.getInputTokens(),
                        extraction.getOutputTokens(),
                        enrichment.model(),
                        enrichment.promptVersion(),
                        enrichment.inputTokens(),
                        enrichment.outputTokens(),
                        enrichment.cacheHits(),
                        enrichment.cacheMisses(),
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
        boolean ingredientAvailable = matched.ingredientDataStatus() == IngredientDataStatus.AVAILABLE;
        RoutinePersonalizationResult.StepAnalysis normalized = aiStep == null ? fallbackStep(source.order()) : aiStep;
        IngredientVerificationStatus verificationStatus = matched.enrichment().ingredientVerificationStatus();
        List<IngredientDetail> ingredients = ingredientAvailable
                ? safe(matched.enrichment().ingredients()).stream()
                        .map(ingredient -> toIngredient(ingredient, verificationStatus))
                        .toList()
                : List.of();
        IngredientStats ingredientStats = ingredientAvailable ? toIngredientStats(ingredients) : null;
        List<ReasonCard> reasons = safe(normalized.reasons()).stream()
                .filter(Objects::nonNull)
                .map(reason -> new ReasonCard(
                        toneOf(reason.assessmentCategory()),
                        reason.assessmentCategory() == null
                                ? AssessmentCategory.CAUTION
                                : reason.assessmentCategory(),
                        userCopy(reason.title(), fallbackReasonTitle(reason.assessmentCategory())),
                        userCopy(reason.description(), fallbackReasonDescription(reason.assessmentCategory())),
                        textOr(reason.evidenceSource(), "PERSONALIZED_ANALYSIS")
                ))
                .limit(3)
                .toList();
        AssessmentCategory primaryCategory = primaryCategory(ingredientStats, reasons);
        reasons = ensureReasonCards(reasons, primaryCategory);
        SafetyLevel safetyLevel = safetyLevel(primaryCategory);
        int skinTypeFit = normalized.scoreBreakdown() == null
                ? 20
                : clamp(normalized.scoreBreakdown().skinTypeFit(), 0, 40);
        int benefitFit = normalized.scoreBreakdown() == null
                ? 18
                : clamp(normalized.scoreBreakdown().benefitFit(), 0, 35);
        int ingredientSafety = ingredientSafety(ingredientAvailable, ingredientStats);
        ScoreBreakdown scoreBreakdown = new ScoreBreakdown(skinTypeFit, benefitFit, ingredientSafety);
        int matchScore = skinTypeFit + benefitFit + ingredientSafety;
        List<String> keyBenefits = normalizeKeyBenefits(normalized.keyBenefits(), source, matched);
        String matchSummary = String.join(" 및 ", keyBenefits);
        ReasonCard primaryReason = selectPrimaryReason(reasons, primaryCategory);

        return new StepResult(
                source.order(),
                source.order(),
                source.startTime(),
                source.endTime(),
                source.category(),
                source.brand(),
                source.productName() == null ? source.category() : source.productName(),
                matched.displayBrand(),
                matched.displayProductName(),
                matched.productResolutionStatus(),
                matched.productResolutionConfidence(),
                matched.imageUrl(),
                matched.productId(),
                source.confidence(),
                source.evidenceSummary(),
                matchScore,
                matchSummary,
                keyBenefits,
                scoreBreakdown,
                safetyLevel,
                primaryCategory,
                categoryTitle(primaryCategory),
                primaryReason.description(),
                reasons,
                matched.ingredientDataStatus(),
                verificationStatus,
                matched.enrichment().marketOrVariant(),
                safe(matched.enrichment().sources()).stream()
                        .map(ingredientSource -> new IngredientSource(
                                ingredientSource.url(), ingredientSource.title(), ingredientSource.sourceType()))
                        .toList(),
                ingredientAvailable ? ingredients.size() : null,
                ingredientStats,
                ingredients
        );
    }

    private List<String> normalizeKeyBenefits(
            List<String> values,
            BeautyRoutineAnalysis.Step source,
            MatchedVideoStep matched
    ) {
        List<String> normalized = safe(values).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank() && value.length() <= 18)
                .filter(value -> isUserCopy(value))
                .filter(value -> !containsIgnoreCase(value, source.productName()))
                .filter(value -> !containsIgnoreCase(value, source.brand()))
                .filter(value -> !containsIgnoreCase(value, matched.displayProductName()))
                .filter(value -> !containsIgnoreCase(value, matched.displayBrand()))
                .map(this::toBenefitPhrase)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(2)
                .toList();
        if (!normalized.isEmpty()) {
            return normalized;
        }
        String purpose = toBenefitPhrase(userCopy(source.purpose(), "피부 컨디션 관리"));
        return List.of(purpose.isBlank() ? "피부 컨디션 관리" : purpose);
    }

    private String toBenefitPhrase(String value) {
        return value
                .replaceAll("(에|에 대해)?\\s*(효과적입니다|도움을 줍니다|좋습니다|적합합니다)[.]?$", "")
                .replaceAll("^(이 제품은|제품은)\\s*", "")
                .trim();
    }

    private int ingredientSafety(boolean ingredientAvailable, IngredientStats stats) {
        if (!ingredientAvailable || stats == null || stats.totalCount() == 0) {
            return 12;
        }
        if (stats.highRiskCount() > 0) {
            return 5;
        }
        if (stats.caution20Count() > 0 || stats.allergenCount() > 0) {
            return 12;
        }
        if (stats.moderateRiskCount() > 0) {
            return 18;
        }
        if (stats.lowRiskCount() == 0 && stats.unknownRiskCount() > 0) {
            return 12;
        }
        return 25;
    }

    private AssessmentCategory primaryCategory(IngredientStats stats, List<ReasonCard> reasons) {
        if ((stats != null && stats.highRiskCount() > 0)
                || hasCategory(reasons, AssessmentCategory.WARNING)) {
            return AssessmentCategory.WARNING;
        }
        if ((stats != null && (stats.caution20Count() > 0 || stats.allergenCount() > 0))
                || hasCategory(reasons, AssessmentCategory.CAUTION)) {
            return AssessmentCategory.CAUTION;
        }
        if (hasCategory(reasons, AssessmentCategory.BENEFICIAL)) {
            return AssessmentCategory.BENEFICIAL;
        }
        return AssessmentCategory.SAFE;
    }

    private boolean hasCategory(List<ReasonCard> reasons, AssessmentCategory category) {
        return reasons.stream().anyMatch(reason -> reason.assessmentCategory() == category);
    }

    private List<ReasonCard> ensureReasonCards(
            List<ReasonCard> reasons,
            AssessmentCategory primaryCategory
    ) {
        List<ReasonCard> completed = new ArrayList<>(reasons);
        if (completed.isEmpty()) {
            completed.add(fallbackReason(primaryCategory));
        }
        if (completed.size() < 2) {
            AssessmentCategory secondary = primaryCategory == AssessmentCategory.SAFE
                    ? AssessmentCategory.BENEFICIAL
                    : AssessmentCategory.SAFE;
            completed.add(fallbackReason(secondary));
        }
        return List.copyOf(completed.stream().limit(3).toList());
    }

    private ReasonCard fallbackReason(AssessmentCategory category) {
        AssessmentCategory safeCategory = category == null ? AssessmentCategory.CAUTION : category;
        return new ReasonCard(
                toneOf(safeCategory),
                safeCategory,
                fallbackReasonTitle(safeCategory),
                fallbackReasonDescription(safeCategory),
                "SERVER_FALLBACK"
        );
    }

    private String fallbackReasonTitle(AssessmentCategory category) {
        return switch (category == null ? AssessmentCategory.CAUTION : category) {
            case SAFE -> "부담이 적은 성분 구성";
            case BENEFICIAL -> "피부 고민에 맞춘 효능";
            case CAUTION -> "사용량과 빈도 조절";
            case WARNING -> "자극 가능 성분 주의";
        };
    }

    private String fallbackReasonDescription(AssessmentCategory category) {
        return switch (category == null ? AssessmentCategory.CAUTION : category) {
            case SAFE -> "일상적인 스킨케어 단계에서 비교적 부담 없이 사용할 수 있어요.";
            case BENEFICIAL -> "현재 피부 타입과 고민에 필요한 관리 효과를 기대할 수 있어요.";
            case CAUTION -> "피부 반응을 살피며 적은 양부터 천천히 사용해 주세요.";
            case WARNING -> "자극 가능성을 줄이기 위해 사용 빈도와 피부 반응을 확인해 주세요.";
        };
    }

    private ReasonCard selectPrimaryReason(List<ReasonCard> reasons, AssessmentCategory primaryCategory) {
        return reasons.stream()
                .filter(reason -> reason.assessmentCategory() == primaryCategory)
                .findFirst()
                .orElseGet(() -> fallbackReason(primaryCategory));
    }

    private SafetyLevel safetyLevel(AssessmentCategory category) {
        return switch (category) {
            case SAFE, BENEFICIAL -> SafetyLevel.SAFE;
            case CAUTION -> SafetyLevel.CAUTION;
            case WARNING -> SafetyLevel.WARNING;
        };
    }

    private String categoryTitle(AssessmentCategory category) {
        return switch (category) {
            case SAFE -> "성분이 안전함";
            case BENEFICIAL -> "피부에 좋음";
            case CAUTION -> "아쉬움·애매";
            case WARNING -> "경고·위험";
        };
    }

    private IngredientDetail toIngredient(
            ProductEnrichmentResult.Ingredient ingredient,
            IngredientVerificationStatus verificationStatus
    ) {
        Optional<RegulationInfo> regulation = regulationInfoCache.find(ingredient.name());
        return new IngredientDetail(
                ingredient.order(),
                ingredient.name(),
                safe(ingredient.purposes()),
                safe(ingredient.skinBenefits()),
                ingredient.riskScore(),
                riskLevel(ingredient.riskScore()),
                ingredient.caution20(),
                ingredient.allergen(),
                verificationStatus == IngredientVerificationStatus.ESTIMATED
                        ? "AI_ESTIMATED"
                        : "AI_WEB_" + verificationStatus.name(),
                regulation.isPresent(),
                regulation.map(this::regulationSummary).orElse(null)
        );
    }

    private IngredientStats toIngredientStats(List<IngredientDetail> ingredients) {
        int low = 0;
        int moderate = 0;
        int high = 0;
        int unknown = 0;
        int caution20 = 0;
        int allergen = 0;
        for (IngredientDetail ingredient : ingredients) {
            switch (ingredient.riskLevel()) {
                case LOW -> low++;
                case MODERATE -> moderate++;
                case HIGH -> high++;
                case UNKNOWN -> unknown++;
            }
            caution20 += ingredient.caution20() ? 1 : 0;
            allergen += ingredient.allergen() ? 1 : 0;
        }
        return new IngredientStats(
                ingredients.size(), low, moderate, high, unknown, caution20, allergen);
    }

    private IngredientRiskLevel riskLevel(Integer score) {
        if (score == null) {
            return IngredientRiskLevel.UNKNOWN;
        }
        if (score <= 2) {
            return IngredientRiskLevel.LOW;
        }
        if (score <= 6) {
            return IngredientRiskLevel.MODERATE;
        }
        return IngredientRiskLevel.HIGH;
    }

    private ReasonTone toneOf(AssessmentCategory category) {
        if (category == null) {
            return ReasonTone.NEUTRAL;
        }
        return switch (category) {
            case SAFE, BENEFICIAL -> ReasonTone.POSITIVE;
            case CAUTION -> ReasonTone.CAUTION;
            case WARNING -> ReasonTone.WARNING;
        };
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
        int replacedCount = 0;
        int missingCount = 0;

        for (MatchedVideoStep matched : matchedSteps) {
            BeautyRoutineAnalysis.Step source = matched.source();
            RoutinePersonalizationResult.InventoryRecommendation recommendation = recommendations.get(source.order());
            InventoryFact selected = recommendation == null ? null : inventoryById.get(recommendation.inventoryId());
            boolean categoryMatches = selected != null
                    && matched.productCategory() != ProductCategory.ETC
                    && matched.productCategory() == categoryResolver.parseStored(selected.category());
            if (!categoryMatches) {
                selected = null;
            }
            OptimizationStatus status;
            Long productId;
            String category;
            String productName;
            String replaceName;
            String brand;
            String imageUrl;
            String reason;

            if (selected != null) {
                status = OptimizationStatus.REPLACED;
                replacedCount++;
                productId = selected.productId();
                category = selected.category();
                productName = selected.productName();
                replaceName = textOr(matched.displayProductName(), source.category());
                brand = selected.brand();
                imageUrl = selected.imageUrl();
                reason = textOr(recommendation.reason(), "같은 카테고리에서 사용할 수 있는 보유 제품입니다.");
            } else {
                status = OptimizationStatus.VIDEO_PRODUCT;
                missingCount++;
                productId = matched.productId();
                category = source.category();
                productName = textOr(matched.displayProductName(), source.category());
                replaceName = null;
                brand = matched.displayBrand();
                imageUrl = matched.imageUrl();
                reason = recommendation == null
                        ? "인벤토리에서 같은 카테고리의 대체 제품을 찾지 못했습니다."
                        : "추천된 인벤토리 제품의 카테고리가 달라 영상 속 제품을 유지합니다.";
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
                    replaceName,
                    brand,
                    imageUrl,
                    reason
            ));
        }

        String summary = replacedCount > 0
                ? "영상 속 제품 중 %d개를 같은 카테고리의 인벤토리 제품으로 교체했습니다.".formatted(replacedCount)
                : "영상 속 루틴과 현재 인벤토리의 조합을 확인했습니다.";
        return new RoutineOptimizationSnapshot(
                newProductCount, replacedCount, missingCount, summary, List.copyOf(steps));
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
                new RoutinePersonalizationResult.ScoreBreakdown(20, 18),
                List.of("피부 컨디션 관리"),
                List.of()
        );
    }

    private List<String> mergeWarnings(List<String> aiWarnings) {
        List<String> warnings = new ArrayList<>(safe(aiWarnings).stream()
                .map(value -> userCopy(value, "제품 라벨과 피부 반응을 함께 확인해 주세요."))
                .toList());
        warnings.add("제품 라벨과 피부 반응을 함께 확인해 주세요.");
        return warnings.stream().filter(Objects::nonNull).filter(value -> !value.isBlank()).distinct().toList();
    }

    private String routineTitle(String value, String coreGoal, List<StepResult> steps) {
        String normalized = textOr(value, "");
        if (isRoutineOverviewCopy(normalized)
                && !containsIgnoreCase(normalized, "분석")
                && normalized.length() <= 40) {
            return normalized;
        }

        String goal = textOr(coreGoal, "").replaceAll("[.!?]+$", "").trim();
        if (isRoutineOverviewCopy(goal) && !containsIgnoreCase(goal, "분석")) {
            String candidate = goal.endsWith("루틴") ? goal : goal + " 루틴";
            if (candidate.length() <= 40) {
                return candidate;
            }
        }

        String benefitTitle = steps.stream()
                .flatMap(step -> safe(step.keyBenefits()).stream())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(benefit -> !benefit.isBlank())
                .distinct()
                .limit(2)
                .reduce((left, right) -> left + "·" + right)
                .map(benefits -> benefits + " 루틴")
                .filter(candidate -> candidate.length() <= 40)
                .orElse("");
        return benefitTitle.isBlank() ? "영상 속 스킨케어 루틴" : benefitTitle;
    }

    private String routineSummary(String value, List<StepResult> steps) {
        String normalized = textOr(value, "");
        if (isRoutineOverviewCopy(normalized) && referencesKnownDetail(normalized, steps)) {
            return normalized;
        }
        return fallbackRoutineSummary(steps);
    }

    private boolean referencesKnownDetail(String value, List<StepResult> steps) {
        for (StepResult step : steps) {
            String productName = concreteProductName(step);
            if (containsIgnoreCase(value, productName)) {
                return true;
            }
            if (safe(step.ingredients()).stream()
                    .map(IngredientDetail::name)
                    .anyMatch(ingredient -> containsIgnoreCase(value, ingredient))) {
                return true;
            }
        }
        return false;
    }

    private String fallbackRoutineSummary(List<StepResult> steps) {
        List<SummaryExample> examples = new ArrayList<>();
        for (StepResult step : steps) {
            String reference = concreteProductName(step);
            if (reference == null || examples.stream().anyMatch(example -> example.reference().equals(reference))) {
                continue;
            }
            String benefit = safe(step.keyBenefits()).stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .findFirst()
                    .orElse("피부 컨디션 관리");
            examples.add(new SummaryExample(benefit, reference));
            if (examples.size() == 2) {
                break;
            }
        }

        String summary;
        if (examples.size() == 2) {
            SummaryExample first = examples.get(0);
            SummaryExample second = examples.get(1);
            summary = "%s(%s), %s(%s) 중심의 %d단계 영상 루틴입니다."
                    .formatted(
                            first.benefit(), first.reference(),
                            second.benefit(), second.reference(),
                            steps.size());
        } else if (examples.size() == 1) {
            SummaryExample example = examples.get(0);
            summary = "%s(%s) 중심의 %d단계 영상 루틴입니다."
                    .formatted(example.benefit(), example.reference(), steps.size());
        } else if (!steps.isEmpty()) {
            String category = textOr(steps.get(0).category(), "스킨케어");
            summary = "%s 단계를 포함해 사용 순서대로 구성한 %d단계 영상 루틴입니다."
                    .formatted(category, steps.size());
        } else {
            summary = "영상 속 스킨케어 단계의 목적과 사용 순서를 정리한 루틴입니다.";
        }

        StepResult cautionStep = steps.stream()
                .filter(step -> step.primaryAssessmentCategory() == AssessmentCategory.CAUTION
                        || step.primaryAssessmentCategory() == AssessmentCategory.WARNING)
                .findFirst()
                .orElse(null);
        if (cautionStep == null) {
            return summary;
        }

        String cautionReference = textOr(
                concreteProductName(cautionStep),
                textOr(cautionStep.category(), "해당 제품"));
        IngredientDetail cautionIngredient = safe(cautionStep.ingredients()).stream()
                .filter(ingredient -> ingredient.riskLevel() == IngredientRiskLevel.HIGH
                        || ingredient.riskLevel() == IngredientRiskLevel.MODERATE
                        || ingredient.caution20()
                        || ingredient.allergen())
                .findFirst()
                .orElse(null);
        if (cautionIngredient != null) {
            return summary + " 다만, %s의 %s 성분은 피부 반응을 살피며 사용량과 빈도를 조절해 주세요."
                    .formatted(cautionReference, cautionIngredient.name());
        }
        return summary + " 다만, %s 단계는 피부 반응을 살피며 적은 양부터 사용해 주세요."
                .formatted(cautionReference);
    }

    private String concreteProductName(StepResult step) {
        if (step.productResolutionStatus() == ProductResolutionStatus.UNRESOLVED) {
            return null;
        }
        if (step.displayProductName() != null && !step.displayProductName().isBlank()) {
            return step.displayProductName().trim();
        }
        return step.productName() == null || step.productName().isBlank() ? null : step.productName().trim();
    }

    private boolean isRoutineOverviewCopy(String value) {
        return value != null
                && !value.isBlank()
                && isUserCopy(value)
                && OWNERSHIP_COPY_MARKERS.stream().noneMatch(marker -> containsIgnoreCase(value, marker));
    }

    private int clamp(int score, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, score));
    }

    private String userCopy(String value, String fallback) {
        String normalized = textOr(value, fallback);
        return isUserCopy(normalized) ? normalized : fallback;
    }

    private boolean isUserCopy(String value) {
        return INTERNAL_COPY_MARKERS.stream().noneMatch(marker -> containsIgnoreCase(value, marker));
    }

    private boolean containsIgnoreCase(String value, String candidate) {
        return value != null
                && candidate != null
                && !candidate.isBlank()
                && value.toLowerCase().contains(candidate.toLowerCase());
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

    private record SummaryExample(String benefit, String reference) {
    }
}
