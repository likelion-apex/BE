package domain.beauty.shortform.application;

import domain.beauty.domain.NormalizedYouTubeVideo;
import domain.beauty.shortform.api.ShortformAnalysisResponses.Applied;
import domain.beauty.shortform.api.ShortformAnalysisResponses.Created;
import domain.beauty.shortform.api.ShortformAnalysisResponses.Detail;
import domain.beauty.shortform.api.ShortformAnalysisResponses.History;
import domain.beauty.shortform.api.ShortformAnalysisResponses.HistoryItem;
import domain.beauty.shortform.api.ShortformAnalysisResponses.Optimization;
import domain.beauty.shortform.api.ShortformAnalysisResponses.ProductDetail;
import domain.beauty.shortform.api.ShortformAnalysisResponses.Status;
import domain.beauty.shortform.api.ShortformAnalysisResponses.VideoPreview;
import domain.beauty.shortform.application.ShortformAnalysisStateService.AnalysisProfile;
import domain.beauty.shortform.application.ShortformAnalysisStateService.CreateResult;
import domain.beauty.shortform.client.YouTubeMetadataClient;
import domain.beauty.shortform.client.YouTubeVideoMetadata;
import domain.beauty.shortform.domain.RoutineOptimizationSnapshot;
import domain.beauty.shortform.domain.RoutineSaveType;
import domain.beauty.shortform.domain.ShortformAnalysis;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot;
import domain.beauty.shortform.domain.ShortformAnalysisRepository.HistorySummary;
import domain.beauty.shortform.domain.ShortformAnalysisStatus;
import domain.beauty.support.YouTubeUrlNormalizer;
import domain.routine.service.RoutineCreationService;
import domain.routine.service.RoutineCreationService.RoutineApplyResult;
import domain.routine.domain.RoutineType;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ShortformAnalysisService {

    private final YouTubeUrlNormalizer urlNormalizer;
    private final YouTubeMetadataClient youtubeMetadataClient;
    private final ShortformAnalysisStateService stateService;
    private final AnalysisFingerprint fingerprint;
    private final ShortformAnalysisJsonMapper jsonMapper;
    private final RoutineCreationService routineCreationService;
    private final ShortformRoutineTypeResolver routineTypeResolver;

    public ShortformAnalysisService(
            YouTubeUrlNormalizer urlNormalizer,
            YouTubeMetadataClient youtubeMetadataClient,
            ShortformAnalysisStateService stateService,
            AnalysisFingerprint fingerprint,
            ShortformAnalysisJsonMapper jsonMapper,
            RoutineCreationService routineCreationService,
            ShortformRoutineTypeResolver routineTypeResolver
    ) {
        this.urlNormalizer = urlNormalizer;
        this.youtubeMetadataClient = youtubeMetadataClient;
        this.stateService = stateService;
        this.fingerprint = fingerprint;
        this.jsonMapper = jsonMapper;
        this.routineCreationService = routineCreationService;
        this.routineTypeResolver = routineTypeResolver;
    }

    public Created create(Long memberId, String videoUrl) {
        NormalizedYouTubeVideo video = urlNormalizer.normalize(videoUrl);
        YouTubeVideoMetadata metadata = youtubeMetadataClient.validate(video.videoId());
        AnalysisProfile profile = stateService.loadProfile(memberId);
        String analysisFingerprint = fingerprint.create(video.videoId(), profile);
        CreateResult result = stateService.createOrReuse(
                memberId,
                video.videoId(),
                video.watchUrl(),
                analysisFingerprint,
                thumbnailUrl(video.videoId(), metadata.thumbnailUrl()));
        ShortformAnalysis analysis = result.analysis();
        return new Created(
                analysis.getId(), analysis.getStatus(), analysis.getProgress(), !result.created());
    }

    public VideoPreview preview(String videoUrl) {
        NormalizedYouTubeVideo video = urlNormalizer.normalize(videoUrl);
        YouTubeVideoMetadata metadata = youtubeMetadataClient.validate(video.videoId());
        return new VideoPreview(
                metadata.thumbnailUrl(),
                metadata.title(),
                metadata.publisher(),
                YouTubeVideoPreviewFormatter.formatViewCount(metadata.viewCount()),
                YouTubeVideoPreviewFormatter.formatDuration(metadata.duration())
        );
    }

    public Status status(Long memberId, Long analysisId) {
        return toStatus(stateService.getOwned(memberId, analysisId));
    }

    public Status cancel(Long memberId, Long analysisId) {
        return toStatus(stateService.cancel(memberId, analysisId));
    }

    public History recent(Long memberId) {
        List<HistoryItem> items = stateService.recent(memberId).stream()
                .map(this::toHistoryItem)
                .toList();
        return new History(items);
    }

    public Detail detail(Long memberId, Long analysisId) {
        ShortformAnalysis analysis = stateService.getOwned(memberId, analysisId);
        stateService.requireCompleted(analysis);
        return new Detail(
                analysis.getId(),
                analysis.getStatus(),
                analysis.getCreatedAt(),
                analysis.getCompletedAt(),
                readAnalysis(analysis)
        );
    }

    public ProductDetail productDetail(Long memberId, Long analysisId, Long resultId) {
        ShortformAnalysisSnapshot snapshot = detail(memberId, analysisId).result();
        ShortformAnalysisSnapshot.StepResult result = snapshot.steps().stream()
                .filter(step -> step.resultId() == resultId)
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.SHORTFORM_ANALYSIS_NOT_FOUND));
        return new ProductDetail(analysisId, result, snapshot.disclaimer());
    }

    public Optimization optimize(Long memberId, Long analysisId) {
        ShortformAnalysis analysis = stateService.markOptimized(memberId, analysisId);
        return new Optimization(
                analysis.getId(),
                analysis.getOptimizedAt(),
                jsonMapper.read(analysis.getOptimizationJson(), RoutineOptimizationSnapshot.class)
        );
    }

    public Applied apply(
            Long memberId,
            Long analysisId,
            RoutineSaveType saveType,
            RoutineType requestedRoutineType
    ) {
        ShortformAnalysis analysis = stateService.getOwned(memberId, analysisId);
        stateService.requireCompleted(analysis);
        if (analysis.getOptimizedAt() == null) {
            throw new CustomException(ErrorCode.SHORTFORM_OPTIMIZATION_REQUIRED);
        }
        RoutineType routineType = routineTypeResolver.resolve(requestedRoutineType);
        RoutineApplyResult result = routineCreationService.create(
                memberId,
                analysisId,
                saveType,
                routineType,
                readAnalysis(analysis),
                jsonMapper.read(analysis.getOptimizationJson(), RoutineOptimizationSnapshot.class)
        );
        return new Applied(
                analysisId,
                result.routineId(),
                result.saveType(),
                result.routineType(),
                result.status(),
                result.reused()
        );
    }

    private Status toStatus(ShortformAnalysis analysis) {
        return new Status(
                analysis.getId(),
                analysis.getStatus(),
                analysis.getProgress(),
                analysis.getStatusMessage(),
                analysis.getErrorCode(),
                analysis.getErrorMessage(),
                analysis.getUpdatedAt()
        );
    }

    private HistoryItem toHistoryItem(HistorySummary analysis) {
        String thumbnailUrl = thumbnailUrl(analysis.getVideoId(), analysis.getThumbnailUrl());
        if (analysis.getStatus() != ShortformAnalysisStatus.COMPLETED) {
            return new HistoryItem(
                    analysis.getAnalysisId(), analysis.getStatus(), thumbnailUrl,
                    null, 0, null, analysis.getCreatedAt());
        }
        return new HistoryItem(
                analysis.getAnalysisId(),
                analysis.getStatus(),
                thumbnailUrl,
                analysis.getTitle() == null ? "이전 분석 결과" : analysis.getTitle(),
                analysis.getStepCount() == null ? 0 : analysis.getStepCount(),
                analysis.getOverallScore(),
                analysis.getCreatedAt()
        );
    }

    private String thumbnailUrl(String videoId, String cachedUrl) {
        if (cachedUrl != null && !cachedUrl.isBlank()) {
            return cachedUrl.trim();
        }
        return "https://i.ytimg.com/vi/%s/hqdefault.jpg".formatted(videoId);
    }

    private ShortformAnalysisSnapshot readAnalysis(ShortformAnalysis analysis) {
        return jsonMapper.read(analysis.getResultJson(), ShortformAnalysisSnapshot.class);
    }
}
