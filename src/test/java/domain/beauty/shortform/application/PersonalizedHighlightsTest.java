package domain.beauty.shortform.application;

import static org.assertj.core.api.Assertions.assertThat;

import domain.beauty.shortform.domain.ShortformAnalysisSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PersonalizedHighlightsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validatesMatchedNamesAndDeduplicatesIngredientsAcrossRoutine() throws Exception {
        ShortformAnalysisSnapshot snapshot = read("""
                [
                  {
                    "resultId":1,
                    "order":1,
                    "productResolutionConfidence":0,
                    "identificationConfidence":0,
                    "matchScore":0,
                    "reasons":[],
                    "ingredients":[
                      {"order":1,"name":"히알루론산","caution20":false,"allergen":false,"regulated":false},
                      {"order":2,"name":"향료","caution20":false,"allergen":true,"regulated":false}
                    ]
                  },
                  {
                    "resultId":2,
                    "order":2,
                    "productResolutionConfidence":0,
                    "identificationConfidence":0,
                    "matchScore":0,
                    "reasons":[],
                    "ingredients":[
                      {"order":1,"name":" 히알루론산 ","caution20":false,"allergen":false,"regulated":false},
                      {"order":2,"name":"향료","caution20":false,"allergen":true,"regulated":false}
                    ]
                  }
                ]
                """);

        List<String> highlights = PersonalizedHighlights.calculate(
                "지성",
                snapshot.steps(),
                Map.of(
                        1, List.of("히알루론산", "입력에 없는 성분"),
                        2, List.of(" 히알루론산 ")));

        assertThat(highlights).containsExactly(
                "지성 맞춤 성분 1개 매칭",
                "알레르기 유발 성분 1개");
    }

    @Test
    void correctsLegacyBenefitHighlightsUsingOnlyGroundedBeneficialCards() throws Exception {
        ShortformAnalysisSnapshot snapshot = read("""
                [{
                  "resultId":1,
                  "order":1,
                  "productResolutionConfidence":0,
                  "identificationConfidence":0,
                  "matchScore":0,
                  "reasons":[
                    {
                      "assessmentCategory":"BENEFICIAL",
                      "title":"히알루론산의 보습 효과",
                      "description":"히알루론산이 수분 공급에 도움을 줍니다."
                    },
                    {
                      "assessmentCategory":"SAFE",
                      "title":"판테놀은 부담이 적어요",
                      "description":"판테놀 정보가 확인됐어요."
                    }
                  ],
                  "ingredients":[
                    {"order":1,"name":"히알루론산","caution20":false,"allergen":false,"regulated":false},
                    {"order":2,"name":"판테놀","caution20":false,"allergen":false,"regulated":false},
                    {"order":3,"name":"향료","caution20":false,"allergen":true,"regulated":false}
                  ]
                }]
                """);

        List<String> highlights = PersonalizedHighlights.personalizeAnalysis(
                "장선우", "지성", snapshot);

        assertThat(highlights).containsExactly(
                "지성 맞춤 성분 1개 매칭",
                "장선우님 피부 알레르기 유발 성분 1개");
    }

    @Test
    void addsCurrentNicknameToStoredCanonicalCountsWithoutDuplicatingHonorific() throws Exception {
        ShortformAnalysisSnapshot snapshot = objectMapper.readValue("""
                {
                  "schemaVersion":"3.0",
                  "overallScore":0,
                  "highlights":["수부지 맞춤 성분 8개 매칭","알레르기 유발 성분 5개"],
                  "steps":[]
                }
                """, ShortformAnalysisSnapshot.class);

        assertThat(PersonalizedHighlights.personalizeAnalysis("윤지님", "민감성", snapshot))
                .containsExactly(
                        "수부지 맞춤 성분 8개 매칭",
                        "윤지님 피부 알레르기 유발 성분 5개");
    }

    private ShortformAnalysisSnapshot read(String steps) throws Exception {
        return objectMapper.readValue("""
                {
                  "schemaVersion":"3.0",
                  "videoId":"video",
                  "youtubeUrl":"https://youtube.com/watch?v=video",
                  "title":"루틴",
                  "tag":"맞춤",
                  "overallScore":70,
                  "highlights":["수분 공급","피부 진정"],
                  "coreGoal":"관리",
                  "synergyCombo":"조합",
                  "summary":"요약",
                  "warnings":[],
                  "disclaimer":"안내",
                  "steps":%s,
                  "aiMetadata":null
                }
                """.formatted(steps), ShortformAnalysisSnapshot.class);
    }
}
