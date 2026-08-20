package domain.inventory.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class OpenAiPersonalizedAnalysisClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parseResultSucceedsWithFewerThanThreeValidEntries() {
        // score만 유효하면 keywords가 부족해도 성공으로 간주한다. 부족분은 InventoryService가
        // 기본 키워드로 채우므로, 여기서 실패(null)로 만들어 매번 Gemini/Groq까지 순차 호출하게
        // 하지 않는다(실제 배포 환경에서 지연시간/네트워크 실패를 유발했던 원인).
        ObjectNode payload = mapper.createObjectNode();
        payload.put("score", 80);
        var keywords = payload.putArray("keywords");
        keywords.addObject().put("keyword", "보습").put("reason", "건성에 맞음");
        keywords.addObject().put("keyword", "저자극").put("reason", "민감성 성분 없음");

        PersonalizedAnalysisResult result = OpenAiPersonalizedAnalysisClient.parseResult(payload);

        assertThat(result).isNotNull();
        assertThat(result.score()).isEqualTo(80);
        assertThat(result.keywords()).containsExactly(
                new PersonalizedAnalysisResult.Keyword("보습", "건성에 맞음"),
                new PersonalizedAnalysisResult.Keyword("저자극", "민감성 성분 없음"));
    }

    @Test
    void parseResultReturnsNullWhenKeywordsArrayIsEmpty() {
        // 유효 keyword가 단 하나도 없는 완전히 빈 응답은 해당 provider가 응답을 만들어내지
        // 못한 것으로 보고 null을 반환해 다음 provider로 폴백시킨다.
        ObjectNode payload = mapper.createObjectNode();
        payload.put("score", 80);
        payload.putArray("keywords");

        PersonalizedAnalysisResult result = OpenAiPersonalizedAnalysisClient.parseResult(payload);

        assertThat(result).isNull();
    }

    @Test
    void parseResultReturnsNullWhenAllEntriesHaveBlankKeyword() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("score", 80);
        var keywords = payload.putArray("keywords");
        keywords.addObject().put("keyword", "").put("reason", "빈 키워드");
        keywords.addObject().put("keyword", "  ").put("reason", "공백 키워드");

        PersonalizedAnalysisResult result = OpenAiPersonalizedAnalysisClient.parseResult(payload);

        assertThat(result).isNull();
    }

    @Test
    void parseResultDropsEntriesWithBlankKeyword() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("score", 80);
        var keywords = payload.putArray("keywords");
        keywords.addObject().put("keyword", "보습").put("reason", "건성에 맞음");
        keywords.addObject().put("keyword", "").put("reason", "빈 키워드");
        keywords.addObject().put("keyword", "저알러지").put("reason", "알레르기 유발 성분 없음");

        PersonalizedAnalysisResult result = OpenAiPersonalizedAnalysisClient.parseResult(payload);

        assertThat(result).isNotNull();
        assertThat(result.keywords()).containsExactly(
                new PersonalizedAnalysisResult.Keyword("보습", "건성에 맞음"),
                new PersonalizedAnalysisResult.Keyword("저알러지", "알레르기 유발 성분 없음"));
    }

    @Test
    void parseResultKeepsKeywordWithFallbackReasonWhenReasonIsBlank() {
        // keyword는 있는데 reason만 비어 있으면 항목을 통째로 버리지 않고 대체 문구로 채워 살린다.
        ObjectNode payload = mapper.createObjectNode();
        payload.put("score", 80);
        var keywords = payload.putArray("keywords");
        keywords.addObject().put("keyword", "저자극").put("reason", "");

        PersonalizedAnalysisResult result = OpenAiPersonalizedAnalysisClient.parseResult(payload);

        assertThat(result).isNotNull();
        assertThat(result.keywords()).hasSize(1);
        assertThat(result.keywords().get(0).keyword()).isEqualTo("저자극");
        assertThat(result.keywords().get(0).reason()).isNotBlank();
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
