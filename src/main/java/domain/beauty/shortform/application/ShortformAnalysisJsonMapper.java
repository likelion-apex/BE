package domain.beauty.shortform.application;

import global.exception.CustomException;
import global.exception.ErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ShortformAnalysisJsonMapper {

    private final ObjectMapper objectMapper;

    public ShortformAnalysisJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new CustomException(ErrorCode.SHORTFORM_INVALID_AI_RESPONSE, "분석 결과를 저장할 수 없습니다.");
        }
    }

    public <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            throw new CustomException(ErrorCode.SHORTFORM_ANALYSIS_NOT_COMPLETED);
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException exception) {
            throw new CustomException(ErrorCode.SHORTFORM_INVALID_AI_RESPONSE, "저장된 분석 결과를 읽을 수 없습니다.");
        }
    }
}
