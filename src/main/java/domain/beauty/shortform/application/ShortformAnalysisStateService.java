package domain.beauty.shortform.application;

import domain.beauty.shortform.domain.ShortformAnalysis;
import domain.beauty.shortform.domain.ShortformAnalysisRepository;
import domain.beauty.shortform.domain.ShortformAnalysisStatus;
import domain.beauty.shortform.domain.VideoRoutineExtraction;
import domain.inventory.Inventory;
import domain.inventory.InventoryRepository;
import domain.member.Member;
import domain.member.MemberRepository;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortformAnalysisStateService {

    private final ShortformAnalysisRepository analysisRepository;
    private final MemberRepository memberRepository;
    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ShortformAnalysisStateService(
            ShortformAnalysisRepository analysisRepository,
            MemberRepository memberRepository,
            InventoryRepository inventoryRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.analysisRepository = analysisRepository;
        this.memberRepository = memberRepository;
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public AnalysisProfile loadProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.getSkinType() == null) {
            throw new CustomException(ErrorCode.SHORTFORM_PROFILE_INCOMPLETE);
        }
        return new AnalysisProfile(
                member.getId(),
                member.getSkinType().getLabel(),
                member.getSkinConcerns().stream().map(concern -> concern.getLabel()).sorted().toList(),
                inventoryRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId)
                        .stream().map(InventoryFact::from).toList()
        );
    }

    @Transactional
    public CreateResult createOrReuse(
            Long memberId,
            String videoId,
            String youtubeUrl,
            String fingerprint,
            String thumbnailUrl
    ) {
        ShortformAnalysis latest = analysisRepository
                .findFirstByMemberIdAndAnalysisFingerprintOrderByCreatedAtDesc(memberId, fingerprint)
                .orElse(null);
        if (latest != null
                && latest.getStatus() != ShortformAnalysisStatus.FAILED
                && latest.getStatus() != ShortformAnalysisStatus.CANCELLED) {
            latest.cacheThumbnailUrl(thumbnailUrl);
            return new CreateResult(latest, false);
        }
        return create(memberId, videoId, youtubeUrl, fingerprint, thumbnailUrl);
    }

    private CreateResult create(
            Long memberId,
            String videoId,
            String youtubeUrl,
            String fingerprint,
            String thumbnailUrl
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        ShortformAnalysis analysis = new ShortformAnalysis(member, videoId, youtubeUrl, fingerprint);
        analysis.cacheThumbnailUrl(thumbnailUrl);
        analysisRepository.save(analysis);
        eventPublisher.publishEvent(new ShortformAnalysisRequested(analysis.getId()));
        return new CreateResult(analysis, true);
    }

    @Transactional
    public CreateResult reanalyze(Long memberId, Long sourceAnalysisId) {
        ShortformAnalysis source = findOwned(memberId, sourceAnalysisId);
        requireCompleted(source);
        ShortformAnalysis latest = analysisRepository
                .findFirstByMemberIdAndAnalysisFingerprintOrderByCreatedAtDesc(
                        memberId, source.getAnalysisFingerprint())
                .orElse(null);
        if (latest != null
                && !latest.getId().equals(source.getId())
                && !latest.getStatus().isTerminal()) {
            return new CreateResult(latest, false);
        }

        ShortformAnalysis replacement = new ShortformAnalysis(
                source.getMember(),
                source.getVideoId(),
                source.getYoutubeUrl(),
                source.getAnalysisFingerprint());
        replacement.cacheThumbnailUrl(source.getThumbnailUrl());
        analysisRepository.save(replacement);
        eventPublisher.publishEvent(new ShortformAnalysisRequested(replacement.getId(), true));
        return new CreateResult(replacement, true);
    }

    @Transactional(readOnly = true)
    public JobContext loadJobContext(Long analysisId) {
        ShortformAnalysis analysis = find(analysisId);
        Member member = analysis.getMember();
        return new JobContext(
                analysis.getId(),
                analysis.getVideoId(),
                analysis.getYoutubeUrl(),
                member.getSkinType() == null ? null : member.getSkinType().getLabel(),
                member.getSkinConcerns().stream().map(concern -> concern.getLabel()).sorted().toList(),
                inventoryRepository.findAllByMemberIdOrderByCreatedAtDesc(member.getId())
                        .stream().map(InventoryFact::from).toList()
        );
    }

    @Transactional
    public void moveTo(Long analysisId, ShortformAnalysisStatus status, String message) {
        find(analysisId).moveTo(status, message);
    }

    @Transactional
    public void attachExtraction(Long analysisId, VideoRoutineExtraction extraction) {
        find(analysisId).attachExtraction(extraction);
    }

    @Transactional
    public void complete(
            Long analysisId,
            VideoRoutineExtraction extraction,
            String resultJson,
            String optimizationJson,
            String resultTitle,
            int resultStepCount,
            int resultOverallScore,
            String openAiModel,
            String openAiPromptVersion,
            long inputTokens,
            long outputTokens
    ) {
        find(analysisId).complete(
                extraction,
                resultJson,
                optimizationJson,
                resultTitle,
                resultStepCount,
                resultOverallScore,
                openAiModel,
                openAiPromptVersion,
                inputTokens,
                outputTokens
        );
    }

    @Transactional
    public void fail(Long analysisId, String errorCode, String message) {
        analysisRepository.findById(analysisId).ifPresent(analysis -> analysis.fail(errorCode, message));
    }

    @Transactional(readOnly = true)
    public boolean isCancelled(Long analysisId) {
        return analysisRepository.findById(analysisId)
                .map(analysis -> analysis.getStatus() == ShortformAnalysisStatus.CANCELLED)
                .orElse(true);
    }

    @Transactional
    public ShortformAnalysis cancel(Long memberId, Long analysisId) {
        ShortformAnalysis analysis = findOwned(memberId, analysisId);
        analysis.cancel();
        return analysis;
    }

    @Transactional
    public ShortformAnalysis markOptimized(
            Long memberId,
            Long analysisId,
            String optimizationJson,
            String reasonVersion
    ) {
        ShortformAnalysis analysis = findOwned(memberId, analysisId);
        requireCompleted(analysis);
        analysis.replaceOptimization(optimizationJson, reasonVersion);
        analysis.markOptimized();
        return analysis;
    }

    @Transactional(readOnly = true)
    public ShortformAnalysis getOwned(Long memberId, Long analysisId) {
        return findOwned(memberId, analysisId);
    }

    @Transactional(readOnly = true)
    public List<ShortformAnalysisRepository.HistorySummary> recent(Long memberId) {
        return analysisRepository.findRecentSummaries(memberId, PageRequest.of(0, 10));
    }

    public void requireCompleted(ShortformAnalysis analysis) {
        if (analysis.getStatus() == ShortformAnalysisStatus.CANCELLED) {
            throw new CustomException(ErrorCode.SHORTFORM_ANALYSIS_CANCELLED);
        }
        if (analysis.getStatus() != ShortformAnalysisStatus.COMPLETED) {
            throw new CustomException(ErrorCode.SHORTFORM_ANALYSIS_NOT_COMPLETED);
        }
    }

    private ShortformAnalysis find(Long analysisId) {
        return analysisRepository.findById(analysisId)
                .orElseThrow(() -> new CustomException(ErrorCode.SHORTFORM_ANALYSIS_NOT_FOUND));
    }

    private ShortformAnalysis findOwned(Long memberId, Long analysisId) {
        return analysisRepository.findByIdAndMemberId(analysisId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.SHORTFORM_ANALYSIS_NOT_FOUND));
    }

    public record CreateResult(ShortformAnalysis analysis, boolean created) {
    }

    public record AnalysisProfile(
            Long memberId,
            String skinType,
            List<String> skinConcerns,
            List<InventoryFact> inventory
    ) {
    }

    public record JobContext(
            Long analysisId,
            String videoId,
            String youtubeUrl,
            String skinType,
            List<String> skinConcerns,
            List<InventoryFact> inventory
    ) {
    }

    public record InventoryFact(
            Long inventoryId,
            Long productId,
            String productName,
            String brand,
            String category,
            String imageUrl
    ) {
        static InventoryFact from(Inventory inventory) {
            return new InventoryFact(
                    inventory.getId(),
                    inventory.getProduct().getId(),
                    inventory.getProduct().getName(),
                    inventory.getProduct().getBrand(),
                    inventory.getProduct().getCategory() == null ? "ETC" : inventory.getProduct().getCategory().name(),
                    inventory.getProduct().getImageUrl()
            );
        }
    }
}
