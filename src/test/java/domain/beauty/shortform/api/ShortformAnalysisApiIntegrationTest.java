import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import domain.beauty.shortform.application.ShortformAnalysisJobHandler;
import domain.beauty.shortform.client.YouTubeMetadataClient;
import domain.beauty.shortform.client.YouTubeVideoMetadata;
import domain.beauty.shortform.domain.ShortformAnalysisRepository;
import domain.member.Member;
import domain.member.MemberRepository;
import domain.member.Provider;
import domain.member.Role;
import domain.member.SkinType;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ApexBeApplication.class)
@AutoConfigureMockMvc
class ShortformAnalysisApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ShortformAnalysisRepository analysisRepository;

    @MockitoBean
    private YouTubeMetadataClient youtubeMetadataClient;

    @MockitoBean
    private ShortformAnalysisJobHandler jobHandler;

    @BeforeEach
    void setUp() {
        analysisRepository.deleteAll();
        memberRepository.deleteAll();
        when(youtubeMetadataClient.validate(anyString())).thenReturn(new YouTubeVideoMetadata(
                "t1S24pgO2XQ", Duration.ofSeconds(45), "테스트 영상", null));
    }

    @Test
    void createsPendingJobAndReusesSameMemberFingerprint() throws Exception {
        Member member = saveMember("member-1");

        mockMvc.perform(post("/api/shortform-analyses")
                        .with(authentication(memberAuthentication(member.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoUrl\":\"https://www.youtube.com/shorts/t1S24pgO2XQ\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.reused").value(false));

        Long analysisId = analysisRepository.findAll().getFirst().getId();
        mockMvc.perform(get("/api/shortform-analyses/{analysisId}/status", analysisId)
                        .with(authentication(memberAuthentication(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(analysisId))
                .andExpect(jsonPath("$.data.progress").value(0));

        mockMvc.perform(post("/api/shortform-analyses")
                        .with(authentication(memberAuthentication(member.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoUrl\":\"https://youtu.be/t1S24pgO2XQ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(analysisId))
                .andExpect(jsonPath("$.data.reused").value(true));
    }

    @Test
    void hidesAnotherMembersAnalysisAsNotFound() throws Exception {
        Member owner = saveMember("owner");
        Member other = saveMember("other");

        mockMvc.perform(post("/api/shortform-analyses")
                        .with(authentication(memberAuthentication(owner.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoUrl\":\"https://www.youtube.com/shorts/t1S24pgO2XQ\"}"))
                .andExpect(status().isAccepted());

        Long analysisId = analysisRepository.findAll().getFirst().getId();
        mockMvc.perform(get("/api/shortform-analyses/{analysisId}", analysisId)
                        .with(authentication(memberAuthentication(other.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS-001"));
    }

    private Member saveMember(String providerId) {
        Member member = Member.builder()
                .nickname("테스터")
                .provider(Provider.KAKAO)
                .providerId(providerId)
                .role(Role.USER)
                .build();
        member.updateSkinType(SkinType.DEHYDRATED_OILY);
        return memberRepository.saveAndFlush(member);
    }

    private UsernamePasswordAuthenticationToken memberAuthentication(Long memberId) {
        return new UsernamePasswordAuthenticationToken(
                memberId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
