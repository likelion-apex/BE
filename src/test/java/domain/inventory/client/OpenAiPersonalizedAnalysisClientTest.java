package domain.inventory.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class OpenAiPersonalizedAnalysisClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parseResultReturnsNullWhenKeywordsHaveFewerThanThreeValidEntries() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("score", 80);
        var keywords = payload.putArray("keywords");
        keywords.addObject().put("keyword", "보습").put("reason", "건성에 맞음");
        keywords.addObject().put("keyword", "저자극").put("reason", "민감성 성분 없음");

        PersonalizedAnalysisResult result = OpenAiPersonalizedAnalysisClient.parseResult(payload);

        assertThat(result).isNull();
    }

    @Test
    void parseResultReturnsNullWhenKeywordsArrayIsEmpty() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("score", 80);
        payload.putArray("keywords");

        PersonalizedAnalysisResult result = OpenAiPersonalizedAnalysisClient.parseResult(payload);

        assertThat(result).isNull();
    }

    @Test
    void parseResultDropsEntriesWithBlankKeywordOrReason() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("score", 80);
        var keywords = payload.putArray("keywords");
        keywords.addObject().put("keyword", "보습").put("reason", "건성에 맞음");
        keywords.addObject().put("keyword", "").put("reason", "빈 키워드");
        keywords.addObject().put("keyword", "저자극").put("reason", "");
        keywords.addObject().put("keyword", "저알러지").put("reason", "알레르기 유발 성분 없음");

        PersonalizedAnalysisResult result = OpenAiPersonalizedAnalysisClient.parseResult(payload);

        assertThat(result).isNull();
    }

    @Test
    void parseResultSucceedsWithExactlyThreeValidKeywords() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("score", 88);
        var keywords = payload.putArray("keywords");
        keywords.addObject().put("keyword", "보습").put("reason", "건성에 맞음");
        keywords.addObject().put("keyword", "저자극").put("reason", "민감성 성분 없음");
        keywords.addObject().put("keyword", "저알러지").put("reason", "알레르기 유발 성분 없음");

        PersonalizedAnalysisResult result = OpenAiPersonalizedAnalysisClient.parseResult(payload);

        assertThat(result).isNotNull();
        assertThat(result.score()).isEqualTo(88);
        assertThat(result.keywords()).containsExactly(
                new PersonalizedAnalysisResult.Keyword("보습", "건성에 맞음"),
                new PersonalizedAnalysisResult.Keyword("저자극", "민감성 성분 없음"),
                new PersonalizedAnalysisResult.Keyword("저알러지", "알레르기 유발 성분 없음"));
    }

    @Test
    void parseResultTruncatesToFirstThreeWhenMoreThanThreeValidKeywords() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("score", 88);
        var keywords = payload.putArray("keywords");
        keywords.addObject().put("keyword", "보습").put("reason", "건성에 맞음");
        keywords.addObject().put("keyword", "저자극").put("reason", "민감성 성분 없음");
        keywords.addObject().put("keyword", "저알러지").put("reason", "알레르기 유발 성분 없음");
        keywords.addObject().put("keyword", "비건인증").put("reason", "비건 인증 성분");

        PersonalizedAnalysisResult result = OpenAiPersonalizedAnalysisClient.parseResult(payload);

        assertThat(result).isNotNull();
        assertThat(result.keywords()).hasSize(3);
        assertThat(result.keywords()).extracting(PersonalizedAnalysisResult.Keyword::keyword)
                .containsExactly("보습", "저자극", "저알러지");
    }

    @Test
    void parseResultReturnsNullWhenScoreIsMissing() {
        ObjectNode payload = mapper.createObjectNode();
        var keywords = payload.putArray("keywords");
        keywords.addObject().put("keyword", "보습").put("reason", "건성에 맞음");
        keywords.addObject().put("keyword", "저자극").put("reason", "민감성 성분 없음");
        keywords.addObject().put("keyword", "저알러지").put("reason", "알레르기 유발 성분 없음");

        PersonalizedAnalysisResult result = OpenAiPersonalizedAnalysisClient.parseResult(payload);

        assertThat(result).isNull();
    }

    @Test
    void parseResultReturnsNullForNonObjectInput() {
        assertThat(OpenAiPersonalizedAnalysisClient.parseResult(null)).isNull();
    }

    @Test
    void buildUserContentFallsBackToDefaultsWhenDataMissing() {
        String content = OpenAiPersonalizedAnalysisClient.buildUserContent("바닥 토너", List.of(), null, null);

        assertThat(content).contains("바닥 토너").contains("알 수 없음").contains("미입력");
    }
}
