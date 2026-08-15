import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import domain.beauty.domain.BeautyRoutineAnalysis.IdentificationLevel;
import domain.beauty.domain.BeautyRoutineAnalysisResult;
import domain.beauty.shortform.api.ShortformAnalysisResponses.Created;
import domain.beauty.shortform.api.ShortformAnalysisResponses.Detail;
import domain.beauty.shortform.application.ShortformAnalysisJsonMapper;
import domain.beauty.shortform.application.ShortformAnalysisService;
import domain.beauty.shortform.application.ShortformProductEnrichmentService;
import domain.beauty.shortform.domain.AssessmentCategory;
import domain.beauty.shortform.domain.IngredientDataStatus;
import domain.beauty.shortform.domain.IngredientRiskLevel;
import domain.beauty.shortform.domain.IngredientVerificationStatus;
import domain.beauty.shortform.domain.SafetyLevel;
import domain.beauty.shortform.domain.ShortformAnalysis;
import domain.beauty.shortform.domain.ShortformAnalysisRepository;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot;
import domain.beauty.shortform.domain.ShortformAnalysisStatus;
import domain.beauty.shortform.domain.VideoRoutineExtractionRepository;
import domain.member.Member;
import domain.member.MemberRepository;
import domain.member.Provider;
import domain.member.Role;
import domain.member.SkinConcern;
import domain.member.SkinType;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("local")
@SpringBootTest(classes = ApexBeApplication.class)
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_AI_TEST", matches = "true")
class ShortformAnalysisLiveApiTest {

    private static final String DEFAULT_VIDEO_URL = "https://www.youtube.com/shorts/AsQSwQRW-Eg";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ShortformAnalysisRepository analysisRepository;
    @Autowired
    private VideoRoutineExtractionRepository extractionRepository;
    @Autowired
    private ShortformAnalysisService analysisService;
    @Autowired
    private ShortformProductEnrichmentService enrichmentService;
    @Autowired
    private ShortformAnalysisJsonMapper jsonMapper;

    @Test
    void analyzesRealShortAndReusesProductEnrichmentCache() throws Exception {
        assertRequiredEnvironment();
        Member member = Member.builder()
                .nickname("실API테스터")
                .provider(Provider.KAKAO)
                .providerId("live-shortform-" + System.nanoTime())
                .role(Role.USER)
                .build();
        member.updateSkinType(SkinType.DRY);
        member.updateSkinConcerns(Set.of(SkinConcern.SENSITIVE, SkinConcern.DRYNESS));
        member = memberRepository.saveAndFlush(member);

        String videoUrl = System.getenv().getOrDefault("SHORTFORM_LIVE_VIDEO_URL", DEFAULT_VIDEO_URL);
        Created created = analysisService.create(member.getId(), videoUrl);
        ShortformAnalysis completed = awaitTerminal(created.analysisId());
        if (completed.getStatus() == ShortformAnalysisStatus.FAILED) {
            String extractionSummary = extractionRepository.findAll().stream().findFirst()
                    .map(entity -> {
                        BeautyRoutineAnalysisResult result = jsonMapper.read(
                                entity.getResultJson(), BeautyRoutineAnalysisResult.class);
                        return " / Gemini=" + result.analysis().analysisStatus()
                                + ", routineType=" + result.analysis().routineType()
                                + ", steps=" + result.analysis().steps().size()
                                + ", summary=" + result.analysis().summary();
                    })
                    .orElse("");
            fail("실 API 분석 실패: " + completed.getErrorCode() + " / "
                    + completed.getErrorMessage() + extractionSummary);
        }
        assertThat(completed.getStatus()).isEqualTo(ShortformAnalysisStatus.COMPLETED);

        Detail detail = analysisService.detail(member.getId(), completed.getId());
        ShortformAnalysisSnapshot snapshot = detail.result();
        assertThat(snapshot.steps()).isNotEmpty();
        int expectedOverallScore = (int) Math.round(
                snapshot.steps().stream().mapToInt(ShortformAnalysisSnapshot.StepResult::matchScore)
                        .average().orElse(0));
        assertThat(snapshot.overallScore()).isEqualTo(expectedOverallScore);
        if (snapshot.steps().size() > 1) {
            assertThat(snapshot.steps().stream()
                    .map(ShortformAnalysisSnapshot.StepResult::matchScore)
                    .distinct()
                    .count()).isGreaterThan(1);
        }
        assertThat(snapshot.steps()).anyMatch(step -> step.brand() != null && step.productName() != null);
        assertThat(snapshot.steps()).allSatisfy(step -> {
            assertThat(step.matchScore()).isBetween(0, 100);
            assertThat(step.matchScore()).isEqualTo(
                    step.scoreBreakdown().skinTypeFit()
                            + step.scoreBreakdown().benefitFit()
                            + step.scoreBreakdown().ingredientSafety());
            assertThat(step.keyBenefits()).hasSizeBetween(1, 2);
            assertThat(step.primaryAssessmentCategory()).isIn((Object[]) AssessmentCategory.values());
            assertThat(step.safetyLevel()).isIn((Object[]) SafetyLevel.values());
            assertThat(step.reasons()).hasSizeBetween(2, 3).allSatisfy(reason -> {
                assertThat(reason.assessmentCategory()).isIn((Object[]) AssessmentCategory.values());
                assertThat(reason.title()).doesNotContain("추정", "AI가", "AI는", "대표 처방", "식별");
                assertThat(reason.description()).doesNotContain("추정", "AI가", "AI는", "대표 처방", "식별");
            });
            assertThat(step.matchSummary()).doesNotContain("효과적입니다", "도움을 줍니다");
            if (step.productResolutionConfidence() >= 0.85) {
                assertThat(step.displayProductName()).isNotBlank();
            }
            if (step.ingredientDataStatus() == IngredientDataStatus.AVAILABLE) {
                assertThat(step.ingredients()).isNotEmpty();
                assertThat(step.ingredientVerificationStatus())
                        .isIn(
                                IngredientVerificationStatus.OFFICIAL,
                                IngredientVerificationStatus.CORROBORATED,
                                IngredientVerificationStatus.THIRD_PARTY,
                                IngredientVerificationStatus.ESTIMATED);
                assertThat(step.ingredientSources()).isNotEmpty();
                int distribution = step.ingredientStats().lowRiskCount()
                        + step.ingredientStats().moderateRiskCount()
                        + step.ingredientStats().highRiskCount()
                        + step.ingredientStats().unknownRiskCount();
                assertThat(distribution).isEqualTo(step.ingredientStats().totalCount());
                assertThat(step.ingredients()).allSatisfy(ingredient ->
                        assertThat(ingredient.riskLevel()).isIn((Object[]) IngredientRiskLevel.values()));
            }
        });
        assertThat(snapshot.steps()).anyMatch(step -> step.ingredientDataStatus() == IngredientDataStatus.AVAILABLE);

        BeautyRoutineAnalysisResult extraction = jsonMapper.read(
                extractionRepository.findAll().getFirst().getResultJson(), BeautyRoutineAnalysisResult.class);
        assertThat(extraction.analysis().steps())
                .anyMatch(step -> step.identificationLevel() == IdentificationLevel.EXACT_PRODUCT);
        ShortformProductEnrichmentService.BatchResult cached = enrichmentService.getOrEnrich(
                extraction.analysis().steps());
        assertThat(cached.cacheMisses()).isZero();
        assertThat(cached.inputTokens()).isZero();
        assertThat(cached.outputTokens()).isZero();
    }

    private ShortformAnalysis awaitTerminal(Long analysisId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(4));
        while (Instant.now().isBefore(deadline)) {
            ShortformAnalysis analysis = analysisRepository.findById(analysisId).orElseThrow();
            if (analysis.getStatus().isTerminal()) {
                return analysis;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("실 API 분석이 제한 시간 안에 끝나지 않았습니다.");
    }

    private void assertRequiredEnvironment() {
        for (String name : Set.of(
                "OPENAI_API_KEY", "GEMINI_API_KEY", "YOUTUBE_API_KEY", "MFDS_SERVICE_KEY",
                "OPENAI_API_URL", "OPENAI_PRODUCT_API_URL", "GEMINI_BASE_URL", "YOUTUBE_BASE_URL",
                "MFDS_INGREDIENT_INFO_URL", "MFDS_REGULATION_INFO_URL")) {
            assertThat(System.getenv(name)).as(name + " 환경변수").isNotBlank();
        }
        assertThat(System.getenv("MFDS_ENABLED")).isEqualToIgnoringCase("true");
    }
}
