import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import domain.beauty.shortform.application.ShortformAnalysisJobHandler;
import domain.beauty.shortform.client.YouTubeMetadataClient;
import domain.beauty.shortform.client.YouTubeVideoMetadata;
import domain.beauty.shortform.domain.ShortformAnalysis;
import domain.beauty.shortform.domain.ShortformAnalysisRepository;
import domain.member.Member;
import domain.member.MemberRepository;
import domain.member.Provider;
import domain.member.Role;
import domain.member.SkinType;
import java.math.BigInteger;
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
import org.springframework.test.util.ReflectionTestUtils;

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
                "t1S24pgO2XQ",
                Duration.ofSeconds(58),
                "테스트 영상",
                "https://img.example.test/video.jpg",
                "테스트 채널",
                BigInteger.valueOf(123_456)
        ));
    }

    @Test
    void previewsVideoMetadataWithoutCreatingAnalysis() throws Exception {
        Member member = saveMember("preview-member");

        mockMvc.perform(post("/api/shortform-analyses/preview")
                        .with(authentication(memberAuthentication(member.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoUrl\":\"https://www.youtube.com/shorts/t1S24pgO2XQ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thumbnailUrl").value("https://img.example.test/video.jpg"))
                .andExpect(jsonPath("$.data.title").value("테스트 영상"))
                .andExpect(jsonPath("$.data.publisher").value("테스트 채널"))
                .andExpect(jsonPath("$.data.viewCount").value("12.3만회"))
                .andExpect(jsonPath("$.data.duration").value("0:58"));

        assertThat(analysisRepository.count()).isZero();
        verifyNoInteractions(jobHandler);
    }

    @Test
    void requiresAuthenticationForVideoPreview() throws Exception {
        mockMvc.perform(post("/api/shortform-analyses/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoUrl\":\"https://www.youtube.com/shorts/t1S24pgO2XQ\"}"))
                .andExpect(status().isUnauthorized());
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

        ShortformAnalysis pending = analysisRepository.findAll().getFirst();
        Long analysisId = pending.getId();
        mockMvc.perform(get("/api/shortform-analyses/{analysisId}/status", analysisId)
                        .with(authentication(memberAuthentication(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(analysisId))
                .andExpect(jsonPath("$.data.progress").value(0));

        ReflectionTestUtils.setField(pending, "thumbnailUrl", null);
        analysisRepository.saveAndFlush(pending);

        mockMvc.perform(post("/api/shortform-analyses")
                        .with(authentication(memberAuthentication(member.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoUrl\":\"https://youtu.be/t1S24pgO2XQ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(analysisId))
                .andExpect(jsonPath("$.data.reused").value(true));

        assertThat(analysisRepository.findById(analysisId).orElseThrow().getThumbnailUrl())
                .isEqualTo("https://img.example.test/video.jpg");
    }

    @Test
    void returnsCachedThumbnailInHistoryWithoutYouTubeRequest() throws Exception {
        Member member = saveMember("thumbnail-member");

        mockMvc.perform(post("/api/shortform-analyses")
                        .with(authentication(memberAuthentication(member.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoUrl\":\"https://www.youtube.com/shorts/t1S24pgO2XQ\"}"))
                .andExpect(status().isAccepted());

        ShortformAnalysis analysis = analysisRepository.findAll().getFirst();
        assertThat(analysis.getThumbnailUrl()).isEqualTo("https://img.example.test/video.jpg");
        clearInvocations(youtubeMetadataClient);

        mockMvc.perform(get("/api/shortform-analyses")
                        .with(authentication(memberAuthentication(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].thumbnailUrl")
                        .value("https://img.example.test/video.jpg"));

        verifyNoInteractions(youtubeMetadataClient);
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

    @Test
    void returnsRecentHistoryWithoutReadingLegacyResultJson() throws Exception {
        Member member = saveMember("legacy-member");
        ShortformAnalysis legacy = new ShortformAnalysis(
                member,
                "legacyVideo",
                "https://www.youtube.com/watch?v=legacyVideo",
                "legacy-fingerprint"
        );
        legacy.complete(
                null,
                "{broken-json",
                "{}",
                "구버전 루틴",
                4,
                77,
                "gpt-test",
                "1.0",
                1,
                1
        );
        ReflectionTestUtils.setField(legacy, "resultTitle", null);
        ReflectionTestUtils.setField(legacy, "resultStepCount", null);
        ReflectionTestUtils.setField(legacy, "resultOverallScore", null);
        analysisRepository.saveAndFlush(legacy);

        mockMvc.perform(get("/api/shortform-analyses")
                        .with(authentication(memberAuthentication(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value("이전 분석 결과"))
                .andExpect(jsonPath("$.data.items[0].thumbnailUrl")
                        .value("https://i.ytimg.com/vi/legacyVideo/hqdefault.jpg"))
                .andExpect(jsonPath("$.data.items[0].stepCount").value(0))
                .andExpect(jsonPath("$.data.items[0].overallScore").doesNotExist());

        verifyNoInteractions(youtubeMetadataClient);
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
