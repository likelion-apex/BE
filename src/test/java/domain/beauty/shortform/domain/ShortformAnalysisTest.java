package domain.beauty.shortform.domain;

import static org.assertj.core.api.Assertions.assertThat;

import domain.member.Member;
import domain.member.Provider;
import domain.member.Role;
import org.junit.jupiter.api.Test;

class ShortformAnalysisTest {

    @Test
    void cachesFirstNonBlankThumbnailUrl() {
        ShortformAnalysis analysis = analysis();

        analysis.cacheThumbnailUrl("  ");
        analysis.cacheThumbnailUrl(" https://img.example.test/first.jpg ");
        analysis.cacheThumbnailUrl("https://img.example.test/second.jpg");

        assertThat(analysis.getThumbnailUrl()).isEqualTo("https://img.example.test/first.jpg");
    }

    @Test
    void cancelledAnalysisCannotBeCompletedByLateWorkerResponse() {
        ShortformAnalysis analysis = analysis();
        analysis.moveTo(ShortformAnalysisStatus.EXTRACTING_VIDEO, "추출 중");
        analysis.cancel();

        analysis.complete(null, "{}", "{}", "테스트 루틴", 2, 80, "gpt-test", "1.0", 1, 1);

        assertThat(analysis.getStatus()).isEqualTo(ShortformAnalysisStatus.CANCELLED);
        assertThat(analysis.getResultJson()).isNull();
    }

    private ShortformAnalysis analysis() {
        Member member = Member.builder()
                .nickname("테스터")
                .provider(Provider.KAKAO)
                .providerId("provider-id")
                .role(Role.USER)
                .build();
        return new ShortformAnalysis(
                member,
                "t1S24pgO2XQ",
                "https://www.youtube.com/watch?v=t1S24pgO2XQ",
                "fingerprint"
        );
    }
}
