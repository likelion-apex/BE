package domain.beauty.shortform.application;

import domain.beauty.application.BeautyRoutineAnalysisService;
import domain.beauty.client.GeminiPromptResources;
import domain.beauty.config.GeminiProperties;
import domain.beauty.domain.BeautyRoutineAnalysisResult;
import domain.beauty.shortform.domain.VideoRoutineExtraction;
import domain.beauty.shortform.domain.VideoRoutineExtractionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class VideoRoutineExtractionService {

    private final VideoRoutineExtractionRepository repository;
    private final BeautyRoutineAnalysisService analysisService;
    private final GeminiProperties geminiProperties;
    private final ShortformAnalysisJsonMapper jsonMapper;

    public VideoRoutineExtractionService(
            VideoRoutineExtractionRepository repository,
            BeautyRoutineAnalysisService analysisService,
            GeminiProperties geminiProperties,
            ShortformAnalysisJsonMapper jsonMapper
    ) {
        this.repository = repository;
        this.analysisService = analysisService;
        this.geminiProperties = geminiProperties;
        this.jsonMapper = jsonMapper;
    }

    public ExtractionResult getOrAnalyze(String videoId, String youtubeUrl) {
        String cacheKey = String.join(":", geminiProperties.getModel(), GeminiPromptResources.VERSION, videoId);
        return repository.findByCacheKey(cacheKey)
                .map(entity -> new ExtractionResult(entity, read(entity)))
                .orElseGet(() -> analyzeAndStore(cacheKey, videoId, youtubeUrl));
    }

    private ExtractionResult analyzeAndStore(String cacheKey, String videoId, String youtubeUrl) {
        BeautyRoutineAnalysisResult result = analysisService.analyze(youtubeUrl);
        VideoRoutineExtraction entity = new VideoRoutineExtraction(
                cacheKey,
                videoId,
                result.youtubeUrl(),
                result.model(),
                GeminiPromptResources.VERSION,
                jsonMapper.write(result),
                result.usage().totalInputTokens(),
                result.usage().totalOutputTokens()
        );
        try {
            return new ExtractionResult(repository.saveAndFlush(entity), result);
        } catch (DataIntegrityViolationException exception) {
            VideoRoutineExtraction existing = repository.findByCacheKey(cacheKey).orElseThrow(() -> exception);
            return new ExtractionResult(existing, read(existing));
        }
    }

    private BeautyRoutineAnalysisResult read(VideoRoutineExtraction entity) {
        return jsonMapper.read(entity.getResultJson(), BeautyRoutineAnalysisResult.class);
    }

    public record ExtractionResult(VideoRoutineExtraction entity, BeautyRoutineAnalysisResult result) {
    }
}
