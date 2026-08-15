package domain.beauty.shortform.application;

import domain.beauty.domain.BeautyRoutineAnalysis;
import domain.beauty.shortform.application.ShortformAnalysisAssembler.AssembledResult;
import domain.beauty.shortform.application.ShortformAnalysisStateService.JobContext;
import domain.beauty.shortform.application.VideoRoutineExtractionService.ExtractionResult;
import domain.beauty.shortform.application.ShortformProductEnrichmentService.BatchResult;
import domain.beauty.shortform.client.OpenAiRoutineAnalysisClient;
import domain.beauty.shortform.client.RoutinePersonalizationInput;
import domain.beauty.shortform.client.RoutinePersonalizationResult.Response;
import domain.beauty.shortform.config.OpenAiRoutineProperties;
import domain.beauty.shortform.domain.ShortformAnalysisStatus;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ShortformAnalysisJobHandler {

    private final ShortformAnalysisStateService stateService;
    private final VideoRoutineExtractionService extractionService;
    private final ShortformProductMatcher productMatcher;
    private final ShortformProductEnrichmentService productEnrichmentService;
    private final ShortformAnalysisAssembler assembler;
    private final OpenAiRoutineAnalysisClient openAiClient;
    private final OpenAiRoutineProperties openAiProperties;
    private final ShortformAnalysisJsonMapper jsonMapper;

    public ShortformAnalysisJobHandler(
            ShortformAnalysisStateService stateService,
            VideoRoutineExtractionService extractionService,
            ShortformProductMatcher productMatcher,
            ShortformProductEnrichmentService productEnrichmentService,
            ShortformAnalysisAssembler assembler,
            OpenAiRoutineAnalysisClient openAiClient,
            OpenAiRoutineProperties openAiProperties,
            ShortformAnalysisJsonMapper jsonMapper
    ) {
        this.stateService = stateService;
        this.extractionService = extractionService;
        this.productMatcher = productMatcher;
        this.productEnrichmentService = productEnrichmentService;
        this.assembler = assembler;
        this.openAiClient = openAiClient;
        this.openAiProperties = openAiProperties;
        this.jsonMapper = jsonMapper;
    }

    @Async("shortformAnalysisExecutor")
    public void analyze(Long analysisId) {
        try {
            JobContext context = stateService.loadJobContext(analysisId);
            if (stopIfCancelled(analysisId)) {
                return;
            }

            stateService.moveTo(
                    analysisId,
                    ShortformAnalysisStatus.EXTRACTING_VIDEO,
                    "영상 속 핵심 제품과 사용 단계를 추출하고 있습니다."
            );
            ExtractionResult extraction = extractionService.getOrAnalyze(
                    context.videoId(), context.youtubeUrl());
            validateSkincare(extraction.result().analysis());
            stateService.attachExtraction(analysisId, extraction.entity());

            if (stopIfCancelled(analysisId)) {
                return;
            }
            stateService.moveTo(
                    analysisId,
                    ShortformAnalysisStatus.MATCHING_PRODUCTS,
                    "영상 제품을 서비스의 제품 정보와 연결하고 있습니다."
            );
            BatchResult enrichment = productEnrichmentService.getOrEnrich(
                    extraction.result().analysis().steps());
            List<MatchedVideoStep> matchedSteps = productMatcher.match(
                    extraction.result().analysis().steps(), enrichment.productsByOrder());

            if (stopIfCancelled(analysisId)) {
                return;
            }
            stateService.moveTo(
                    analysisId,
                    ShortformAnalysisStatus.PERSONALIZING,
                    "피부 타입과 고민에 맞는 루틴인지 분석하고 있습니다."
            );
            RoutinePersonalizationInput input = assembler.toInput(
                    context, extraction.result().analysis(), matchedSteps);
            Response aiResponse = openAiClient.analyze(input);

            if (stopIfCancelled(analysisId)) {
                return;
            }
            stateService.moveTo(
                    analysisId,
                    ShortformAnalysisStatus.OPTIMIZING,
                    "인벤토리 제품과의 궁합을 확인하고 있습니다."
            );
            AssembledResult assembled = assembler.assemble(
                    context, matchedSteps, aiResponse, extraction.entity(), enrichment);

            if (stopIfCancelled(analysisId)) {
                return;
            }
            stateService.complete(
                    analysisId,
                    extraction.entity(),
                    jsonMapper.write(assembled.analysis()),
                    jsonMapper.write(assembled.optimization()),
                    assembled.analysis().title(),
                    assembled.analysis().steps().size(),
                    assembled.analysis().overallScore(),
                    aiResponse.model(),
                    openAiProperties.getRoutinePromptVersion(),
                    aiResponse.inputTokens(),
                    aiResponse.outputTokens()
            );
        } catch (CustomException exception) {
            stateService.fail(
                    analysisId,
                    exception.getErrorCode().getCode(),
                    exception.getMessage()
            );
        } catch (Exception exception) {
            log.error("숏폼 루틴 분석 작업 실패: analysisId={}", analysisId, exception);
            stateService.fail(
                    analysisId,
                    ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                    "예상하지 못한 오류로 루틴 분석에 실패했습니다."
            );
        }
    }

    private void validateSkincare(BeautyRoutineAnalysis analysis) {
        if (analysis == null
                || analysis.routineType() != BeautyRoutineAnalysis.RoutineType.SKINCARE
                || analysis.steps() == null
                || analysis.steps().isEmpty()) {
            throw new CustomException(ErrorCode.SHORTFORM_NOT_SKINCARE);
        }
    }

    private boolean stopIfCancelled(Long analysisId) {
        return stateService.isCancelled(analysisId);
    }
}
