import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import domain.auth.RefreshTokenRepository;
import domain.member.Member;
import domain.member.MemberRepository;
import domain.member.Provider;
import domain.member.Role;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        classes = ApexBeApplication.class,
        properties = {"test-login.enabled=true", "product.seed.enabled=false"}
)
@AutoConfigureMockMvc
class LocalLoginApiIntegrationTest {

    private static final String LOGIN_ID = "soak_api_test";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    private Member member;
    private String testPassword;

    @BeforeEach
    void setUp() {
        testPassword = UUID.randomUUID().toString();
        member = memberRepository.findByProviderAndProviderId(Provider.LOCAL, LOGIN_ID)
                .orElseGet(() -> memberRepository.save(Member.builder()
                        .email("api-test@ssoak.my")
                        .nickname("API테스트")
                        .provider(Provider.LOCAL)
                        .providerId(LOGIN_ID)
                        .role(Role.USER)
                        .build()));
        member.updatePasswordHash(passwordEncoder.encode(testPassword));
        memberRepository.saveAndFlush(member);
        refreshTokenRepository.deleteByMemberId(member.getId());
    }

    @Test
    void loginReissueAndLogoutShareTheExistingJwtLifecycleWithoutLeakingHashes() throws Exception {
        String loginBody = mockMvc.perform(post("/api/auth/local/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"soak_api_test","password":"%s"}
                                """.formatted(testPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewMember").value(false))
                .andExpect(jsonPath("$.data.onboardingRequired").value(true))
                .andExpect(jsonPath("$.data.member.provider").value("LOCAL"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.member.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        var loginJson = objectMapper.readTree(loginBody);
        String accessToken = loginJson.path("data").path("accessToken").asString();
        String refreshToken = loginJson.path("data").path("refreshToken").asString();
        assertThat(loginBody).doesNotContain("password_hash", "passwordHash", testPassword);

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingRequired").value(true));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        assertThat(refreshTokenRepository.findByMemberId(member.getId())).isEqualTo(Optional.empty());
    }

    @Test
    void unknownIdAndWrongPasswordReturnTheSameGenericError() throws Exception {
        assertInvalidCredentials("missing-local-id", "wrong-password");
        assertInvalidCredentials(LOGIN_ID, "wrong-password");
    }

    private void assertInvalidCredentials(String loginId, String password) throws Exception {
        mockMvc.perform(post("/api/auth/local/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"%s"}
                                """.formatted(loginId, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-011"))
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));
    }
}
