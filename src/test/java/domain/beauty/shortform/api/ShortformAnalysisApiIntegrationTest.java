import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import domain.beauty.shortform.application.ShortformAnalysisJobHandler;
import domain.beauty.shortform.client.OpenAiRoutineAnalysisClient;
import domain.beauty.shortform.client.OpenAiOptimizationReasonClient;
import domain.beauty.shortform.client.OptimizationReasonResult;
import domain.beauty.shortform.application.InventoryProductEvidenceService;
import domain.beauty.shortform.client.YouTubeMetadataClient;
import domain.beauty.shortform.client.YouTubeVideoMetadata;
import domain.beauty.shortform.domain.ShortformAnalysis;
import domain.beauty.shortform.domain.ShortformAnalysisRepository;
import domain.member.Member;
import domain.member.MemberRepository;
import domain.member.Provider;
import domain.member.Role;
import domain.member.SkinType;
import domain.inventory.client.OpenAiCategoryClassifier;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(classes = ApexBeApplication.class)
@AutoConfigureMockMvc
class ShortformAnalysisApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ShortformAnalysisRepository analysisRepository;

    @MockitoBean
    private YouTubeMetadataClient youtubeMetadataClient;

    @MockitoBean
    private ShortformAnalysisJobHandler jobHandler;

    @MockitoBean
    private OpenAiRoutineAnalysisClient openAiRoutineAnalysisClient;

    @MockitoBean
    private OpenAiOptimizationReasonClient openAiOptimizationReasonClient;

    @MockitoBean
    private InventoryProductEvidenceService inventoryProductEvidenceService;

    @MockitoBean
    private OpenAiCategoryClassifier openAiCategoryClassifier;

    @BeforeEach
    void setUp() {
        analysisRepository.deleteAll();
        memberRepository.deleteAll();
        when(youtubeMetadataClient.validate(anyString())).thenReturn(new YouTubeVideoMetadata(
                "t1S24pgO2XQ",
                Duration.ofSeconds(58),
                "테스트 영상",
                "https://img.example.test/video.jpg",
                "테스트 채널",
                BigInteger.valueOf(123_456)
        ));
        when(inventoryProductEvidenceService.enrich(any())).thenReturn(Map.of());
    }

    @Test
    void previewsVideoMetadataWithoutCreatingAnalysis() throws Exception {
        Member member = saveMember("preview-member");

        mockMvc.perform(post("/api/shortform-analyses/preview")
                        .with(authentication(memberAuthentication(member.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoUrl\":\"https://www.youtube.com/shorts/t1S24pgO2XQ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thumbnailUrl").value("https://img.example.test/video.jpg"))
                .andExpect(jsonPath("$.data.title").value("테스트 영상"))
                .andExpect(jsonPath("$.data.publisher").value("테스트 채널"))
                .andExpect(jsonPath("$.data.viewCount").value("12.3만회"))
                .andExpect(jsonPath("$.data.duration").value("0:58"));

        assertThat(analysisRepository.count()).isZero();
        verifyNoInteractions(jobHandler);
    }

    @Test
    void requiresAuthenticationForVideoPreview() throws Exception {
        mockMvc.perform(post("/api/shortform-analyses/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoUrl\":\"https://www.youtube.com/shorts/t1S24pgO2XQ\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsPendingJobAndReusesSameMemberFingerprint() throws Exception {
        Member member = saveMember("member-1");

        mockMvc.perform(post("/api/shortform-analyses")
                        .with(authentication(memberAuthentication(member.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoUrl\":\"https://www.youtube.com/shorts/t1S24pgO2XQ\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.reused").value(false));

        ShortformAnalysis pending = analysisRepository.findAll().getFirst();
        Long analysisId = pending.getId();
        mockMvc.perform(get("/api/shortform-analyses/{analysisId}/status", analysisId)
                        .with(authentication(memberAuthentication(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(analysisId))
                .andExpect(jsonPath("$.data.progress").value(0));

        ReflectionTestUtils.setField(pending, "thumbnailUrl", null);
        analysisRepository.saveAndFlush(pending);

        mockMvc.perform(post("/api/shortform-analyses")
                        .with(authentication(memberAuthentication(member.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoUrl\":\"https://youtu.be/t1S24pgO2XQ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(analysisId))
                .andExpect(jsonPath("$.data.reused").value(true));

        assertThat(analysisRepository.findById(analysisId).orElseThrow().getThumbnailUrl())
                .isEqualTo("https://img.example.test/video.jpg");
    }

    @Test
    void returnsCachedThumbnailInHistoryWithoutYouTubeRequest() throws Exception {
        Member member = saveMember("thumbnail-member");

        mockMvc.perform(post("/api/shortform-analyses")
                        .with(authentication(memberAuthentication(member.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoUrl\":\"https://www.youtube.com/shorts/t1S24pgO2XQ\"}"))
                .andExpect(status().isAccepted());

        ShortformAnalysis analysis = analysisRepository.findAll().getFirst();
        assertThat(analysis.getThumbnailUrl()).isEqualTo("https://img.example.test/video.jpg");
        clearInvocations(youtubeMetadataClient);

        mockMvc.perform(get("/api/shortform-analyses")
                        .with(authentication(memberAuthentication(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].thumbnailUrl")
                        .value("https://img.example.test/video.jpg"));

        verifyNoInteractions(youtubeMetadataClient);
    }

    @Test
    void hidesAnotherMembersAnalysisAsNotFound() throws Exception {
        Member owner = saveMember("owner");
        Member other = saveMember("other");

        mockMvc.perform(post("/api/shortform-analyses")
                        .with(authentication(memberAuthentication(owner.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoUrl\":\"https://www.youtube.com/shorts/t1S24pgO2XQ\"}"))
                .andExpect(status().isAccepted());

        Long analysisId = analysisRepository.findAll().getFirst().getId();
        mockMvc.perform(get("/api/shortform-analyses/{analysisId}", analysisId)
                        .with(authentication(memberAuthentication(other.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS-001"));
    }

    @Test
    void returnsRecentHistoryWithoutReadingLegacyResultJson() throws Exception {
        Member member = saveMember("legacy-member");
        ShortformAnalysis legacy = new ShortformAnalysis(
                member,
                "legacyVideo",
                "https://www.youtube.com/watch?v=legacyVideo",
                "legacy-fingerprint"
        );
        legacy.complete(
                null,
                "{broken-json",
                "{}",
                "구버전 루틴",
                4,
                77,
                "gpt-test",
                "1.0",
                1,
                1
        );
        ReflectionTestUtils.setField(legacy, "resultTitle", null);
        ReflectionTestUtils.setField(legacy, "resultStepCount", null);
        ReflectionTestUtils.setField(legacy, "resultOverallScore", null);
        analysisRepository.saveAndFlush(legacy);

        mockMvc.perform(get("/api/shortform-analyses")
                        .with(authentication(memberAuthentication(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value("이전 분석 결과"))
                .andExpect(jsonPath("$.data.items[0].thumbnailUrl")
                        .value("https://i.ytimg.com/vi/legacyVideo/hqdefault.jpg"))
                .andExpect(jsonPath("$.data.items[0].stepCount").value(0))
                .andExpect(jsonPath("$.data.items[0].overallScore").doesNotExist());

        verifyNoInteractions(youtubeMetadataClient);
    }

    @Test
    void normalizesLegacyProductDetailReasonsAndCapacityWithoutExternalCalls() throws Exception {
        Member member = saveMember("legacy-product-detail-member");
        String resultJson = """
                {
                  "schemaVersion": "3.0",
                  "videoId": "legacyDetail",
                  "youtubeUrl": "https://www.youtube.com/watch?v=legacyDetail",
                  "title": "기존 분석",
                  "tag": "스킨케어",
                  "overallScore": 72,
                  "highlights": ["수분 공급"],
                  "coreGoal": "보습",
                  "synergyCombo": "히알루론산",
                  "summary": "기존 분석 결과입니다.",
                  "warnings": [],
                  "disclaimer": "안내",
                  "steps": [{
                    "resultId": 2,
                    "order": 1,
                    "category": "앰플",
                    "productName": "리얼 히알루로닉 블루 100 앰플 100ml",
                    "displayBrand": "웰라쥬",
                    "displayProductName": "리얼 히알루로닉 블루 100 앰플 100ml",
                    "productResolutionStatus": "AI_NORMALIZED",
                    "productResolutionConfidence": 0.9,
                    "identificationConfidence": 0.9,
                    "matchScore": 72,
                    "matchSummary": "수분 공급",
                    "keyBenefits": ["수분 공급"],
                    "scoreBreakdown": {"skinTypeFit": 30, "benefitFit": 25, "ingredientSafety": 17},
                    "safetyLevel": "CAUTION",
                    "primaryAssessmentCategory": "CAUTION",
                    "safetyTitle": "사용 시 주의",
                    "safetySummary": "피부 반응을 살피며 사용해 주세요.",
                    "reasons": [
                      {"tone": "POSITIVE", "assessmentCategory": "SAFE", "title": "낮은 위험도", "description": "부담이 적어요.", "evidenceSource": "AI"},
                      {"tone": "POSITIVE", "assessmentCategory": "BENEFICIAL", "title": "수분 공급", "description": "수분을 공급해요.", "evidenceSource": "AI"}
                    ],
                    "ingredientDataStatus": "AVAILABLE",
                    "ingredientVerificationStatus": "OFFICIAL",
                    "ingredientMarketOrVariant": "한국 판매 제품, 100mL",
                    "ingredientSources": [],
                    "estimatedIngredientCount": 1,
                    "ingredientStats": {"totalCount": 1, "lowRiskCount": 0, "moderateRiskCount": 1, "highRiskCount": 0, "unknownRiskCount": 0, "caution20Count": 1, "allergenCount": 0},
                    "ingredients": [{"order": 1, "name": "향료", "purposes": ["향료"], "skinBenefits": [], "riskScore": 5, "riskLevel": "MODERATE", "caution20": true, "allergen": false, "source": "TEST", "regulated": false}]
                  }],
                  "aiMetadata": null
                }
                """;
        ShortformAnalysis analysis = new ShortformAnalysis(
                member,
                "legacyDetail",
                "https://www.youtube.com/watch?v=legacyDetail",
                "legacy-detail-fingerprint");
        analysis.complete(
                null, resultJson, "{}", "기존 분석", 1, 72,
                "gpt-test", "3.0", 1, 1);
        analysisRepository.saveAndFlush(analysis);
        clearInvocations(
                youtubeMetadataClient,
                jobHandler,
                openAiRoutineAnalysisClient,
                openAiOptimizationReasonClient,
                openAiCategoryClassifier);

        mockMvc.perform(get("/api/shortform-analyses/{analysisId}", analysis.getId())
                        .with(authentication(memberAuthentication(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.steps[0].ingredientMarketOrVariant").value("100ml"))
                .andExpect(jsonPath("$.data.result.steps[0].reasons[2].assessmentCategory").value("CAUTION"))
                .andExpect(jsonPath("$.data.result.steps[0].reasons[2].tone").value("CAUTION"))
                .andExpect(jsonPath("$.data.result.steps[0].reasons[2].description").value(
                        org.hamcrest.Matchers.containsString("향료")));

        mockMvc.perform(get(
                        "/api/shortform-analyses/{analysisId}/results/{resultId}",
                        analysis.getId(), 2)
                        .with(authentication(memberAuthentication(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.ingredientMarketOrVariant").value("100ml"))
                .andExpect(jsonPath("$.data.result.reasons[2].assessmentCategory").value("CAUTION"));

        verifyNoInteractions(
                youtubeMetadataClient,
                jobHandler,
                openAiRoutineAnalysisClient,
                openAiOptimizationReasonClient,
                openAiCategoryClassifier);
    }

    @Test
    void refreshesLegacyOptimizationReasonsOnlyOnceAndKeepsNormalizedProducts() throws Exception {
        Member member = saveMember("optimization-member");
        String resultJson = """
                {
                  "schemaVersion": "3.0",
                  "videoId": "legacyOptimize",
                  "youtubeUrl": "https://www.youtube.com/watch?v=legacyOptimize",
                  "title": "기존 최적화",
                  "tag": "맞춤",
                  "overallScore": 80,
                  "highlights": ["수분 공급"],
                  "coreGoal": "보습",
                  "synergyCombo": "히알루론산",
                  "summary": "영상 제품을 활용한 루틴입니다.",
                  "warnings": [],
                  "disclaimer": "안내",
                  "steps": [
                    {
                      "resultId": 1,
                      "order": 1,
                      "category": "앰플",
                      "productName": "영상 수분 앰플",
                      "displayBrand": "영상 브랜드",
                      "displayProductName": "영상 수분 앰플",
                      "productResolutionStatus": "CATALOG_MATCH",
                      "productResolutionConfidence": 1,
                      "imageUrl": "/video-ampoule.png",
                      "productId": 10,
                      "identificationConfidence": 1,
                      "evidenceSummary": "영상에서 확인",
                      "matchScore": 80,
                      "matchSummary": "수분 공급",
                      "keyBenefits": ["수분 공급"],
                      "scoreBreakdown": {"skinTypeFit": 30, "benefitFit": 25, "ingredientSafety": 25},
                      "safetyLevel": "SAFE",
                      "primaryAssessmentCategory": "SAFE",
                      "safetyTitle": "피부 안전도 평가",
                      "safetySummary": "안전합니다.",
                      "reasons": [],
                      "ingredientDataStatus": "AVAILABLE",
                      "ingredientVerificationStatus": "OFFICIAL",
                      "ingredientSources": [],
                      "estimatedIngredientCount": 1,
                      "ingredientStats": {"totalCount": 1, "lowRiskCount": 1, "moderateRiskCount": 0, "highRiskCount": 0, "unknownRiskCount": 0, "caution20Count": 0, "allergenCount": 0},
                      "ingredients": []
                    },
                    {
                      "resultId": 2,
                      "order": 2,
                      "category": "앰플",
                      "productName": "영상 진정 앰플",
                      "displayBrand": "영상 브랜드",
                      "displayProductName": "영상 진정 앰플",
                      "productResolutionStatus": "CATALOG_MATCH",
                      "productResolutionConfidence": 1,
                      "imageUrl": "/video-soothing.png",
                      "productId": 11,
                      "identificationConfidence": 1,
                      "evidenceSummary": "영상에서 확인",
                      "matchScore": 80,
                      "matchSummary": "피부 진정",
                      "keyBenefits": ["피부 진정"],
                      "scoreBreakdown": {"skinTypeFit": 30, "benefitFit": 25, "ingredientSafety": 25},
                      "safetyLevel": "SAFE",
                      "primaryAssessmentCategory": "SAFE",
                      "safetyTitle": "피부 안전도 평가",
                      "safetySummary": "안전합니다.",
                      "reasons": [],
                      "ingredientDataStatus": "AVAILABLE",
                      "ingredientVerificationStatus": "OFFICIAL",
                      "ingredientSources": [],
                      "estimatedIngredientCount": 1,
                      "ingredientStats": {"totalCount": 1, "lowRiskCount": 1, "moderateRiskCount": 0, "highRiskCount": 0, "unknownRiskCount": 0, "caution20Count": 0, "allergenCount": 0},
                      "ingredients": []
                    }
                  ],
                  "aiMetadata": null
                }
                """;
        String optimizationJson = """
                {
                  "newProductCount": 1,
                  "compatibleCount": 1,
                  "replacedCount": 1,
                  "missingCount": 0,
                  "summary": "기존 요약",
                  "steps": [
                    {
                      "sourceResultId": 1,
                      "order": 1,
                      "status": "COMPATIBLE",
                      "inventoryId": 100,
                      "productId": 20,
                      "category": "SERUM",
                      "productName": "보유 수분 세럼",
                      "brand": "보유 브랜드",
                      "imageUrl": "/owned-serum.png",
                      "reason": "같은 카테고리의 보유 제품입니다."
                    },
                    {
                      "sourceResultId": 2,
                      "order": 2,
                      "status": "REPLACED",
                      "inventoryId": 101,
                      "productId": 21,
                      "category": "SKIN_TONER",
                      "productName": "보유 진정 토너",
                      "brand": "보유 브랜드",
                      "imageUrl": "/owned-toner.png",
                      "reason": "보습 역할이 비슷합니다."
                    }
                  ]
                }
                """;
        ShortformAnalysis analysis = new ShortformAnalysis(
                member,
                "legacyOptimize",
                "https://www.youtube.com/watch?v=legacyOptimize",
                "legacy-optimize-fingerprint"
        );
        analysis.complete(
                null, resultJson, optimizationJson, "기존 최적화", 2, 80,
                "gpt-test", "3.1", 1, 1);
        analysisRepository.saveAndFlush(analysis);
        when(openAiOptimizationReasonClient.generate(any())).thenReturn(
                new OptimizationReasonResult.Response(
                        new OptimizationReasonResult(List.of(
                                new OptimizationReasonResult.StepReason(
                                        1,
                                        "보유하신 보유 수분 세럼으로 영상 수분 앰플의 수분 공급 단계를 추가 구매 없이 이어갈 수 있어요."),
                                new OptimizationReasonResult.StepReason(
                                        2,
                                        "영상 진정 앰플은 피부 진정 역할을 해요. 이 역할을 대신할 확인된 보유 제품이 없어요."))),
                        "gpt-test",
                        20,
                        10));
        clearInvocations(youtubeMetadataClient, jobHandler, openAiRoutineAnalysisClient, openAiCategoryClassifier);

        mockMvc.perform(post("/api/shortform-analyses/{analysisId}/optimize", analysis.getId())
                        .with(authentication(memberAuthentication(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.optimizationReasonVersion").doesNotExist())
                .andExpect(jsonPath("$.data.result.compatibleCount").doesNotExist())
                .andExpect(jsonPath("$.data.result.replacedCount").value(1))
                .andExpect(jsonPath("$.data.result.missingCount").value(1))
                .andExpect(jsonPath("$.data.result.steps[0].status").value("REPLACED"))
                .andExpect(jsonPath("$.data.result.steps[0].productName").value("보유 수분 세럼"))
                .andExpect(jsonPath("$.data.result.steps[0].replaceName").value("영상 수분 앰플"))
                .andExpect(jsonPath("$.data.result.steps[0].reason").value(
                        "보유하신 보유 수분 세럼으로 영상 수분 앰플의 수분 공급 단계를 추가 구매 없이 이어갈 수 있어요."))
                .andExpect(jsonPath("$.data.result.steps[1].status").value("VIDEO_PRODUCT"))
                .andExpect(jsonPath("$.data.result.steps[1].productName").value("영상 진정 앰플"))
                .andExpect(jsonPath("$.data.result.steps[1].replaceName").value((Object) null));

        mockMvc.perform(post("/api/shortform-analyses/{analysisId}/optimize", analysis.getId())
                        .with(authentication(memberAuthentication(member.getId()))))
                .andExpect(status().isOk());

        String savedOptimization = analysisRepository.findById(analysis.getId()).orElseThrow().getOptimizationJson();
        assertThat(savedOptimization).doesNotContain("compatibleCount", "COMPATIBLE", "NO_INVENTORY_MATCH");
        assertThat(analysisRepository.findById(analysis.getId()).orElseThrow().getOptimizationReasonVersion())
                .isEqualTo("3.3");
        verify(openAiOptimizationReasonClient, times(1)).generate(any());
        verifyNoInteractions(youtubeMetadataClient, jobHandler, openAiRoutineAnalysisClient, openAiCategoryClassifier);
    }

    private Member saveMember(String providerId) {
        Member member = Member.builder()
                .nickname("테스터")
                .provider(Provider.KAKAO)
                .providerId(providerId)
                .role(Role.USER)
                .build();
        member.updateSkinType(SkinType.DEHYDRATED_OILY);
        return memberRepository.saveAndFlush(member);
    }

    private UsernamePasswordAuthenticationToken memberAuthentication(Long memberId) {
        return new UsernamePasswordAuthenticationToken(
                memberId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
