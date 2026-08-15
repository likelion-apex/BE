package domain.beauty.shortform.domain;

import static org.assertj.core.api.Assertions.assertThat;

import domain.member.Member;
import domain.member.Provider;
import domain.member.Role;
import org.junit.jupiter.api.Test;

class ShortformAnalysisTest {

    @Test
    void cancelledAnalysisCannotBeCompletedByLateWorkerResponse() {
        Member member = Member.builder()
                .nickname("테스터")
                .provider(Provider.KAKAO)
                .providerId("provider-id")
                .role(Role.USER)
                .build();
        ShortformAnalysis analysis = new ShortformAnalysis(
                member,
                "t1S24pgO2XQ",
                "https://www.youtube.com/watch?v=t1S24pgO2XQ",
                "fingerprint"
        );
        analysis.moveTo(ShortformAnalysisStatus.EXTRACTING_VIDEO, "추출 중");
        analysis.cancel();

        analysis.complete(null, "{}", "{}", "테스트 루틴", 2, 80, "gpt-test", "1.0", 1, 1);

        assertThat(analysis.getStatus()).isEqualTo(ShortformAnalysisStatus.CANCELLED);
        assertThat(analysis.getResultJson()).isNull();
    }
}
