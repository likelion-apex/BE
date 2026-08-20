package domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import domain.auth.RefreshTokenRepository;
import domain.auth.RefreshToken;
import domain.auth.dto.response.TokenResponse;
import domain.member.Member;
import domain.member.MemberRepository;
import domain.member.Provider;
import domain.member.Role;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import security.jwt.JwtTokenProvider;

@ExtendWith(MockitoExtension.class)
class AuthServiceLocalLoginTest {

    private static final String LOGIN_ID = "soak_unit";

    @Mock
    private KakaoOAuthClient kakaoOAuthClient;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;
    private String testPassword;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        testPassword = UUID.randomUUID().toString();
        authService = new AuthService(
                kakaoOAuthClient,
                memberRepository,
                refreshTokenRepository,
                jwtTokenProvider,
                passwordEncoder
        );
        ReflectionTestUtils.setField(authService, "testLoginEnabled", true);
    }

    @Test
    void issuesExistingJwtTokensAndReportsOnboardingState() {
        Member member = localMember(passwordEncoder.encode(testPassword));
        when(memberRepository.findByProviderAndProviderId(Provider.LOCAL, LOGIN_ID))
                .thenReturn(Optional.of(member));
        when(jwtTokenProvider.createAccessToken(member)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(member)).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(3_600_000L);
        when(jwtTokenProvider.getRefreshTokenExpiration()).thenReturn(1_209_600_000L);
        when(refreshTokenRepository.findByMemberId(member.getId())).thenReturn(Optional.empty());

        TokenResponse response = authService.loginWithLocal("  SOAK_UNIT ", testPassword);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.isNewMember()).isFalse();
        assertThat(response.onboardingRequired()).isTrue();
        assertThat(response.member().provider()).isEqualTo("LOCAL");
    }

    @Test
    void unknownIdAndWrongPasswordUseTheSamePublicError() {
        Member member = localMember(passwordEncoder.encode(testPassword));
        when(memberRepository.findByProviderAndProviderId(Provider.LOCAL, "missing"))
                .thenReturn(Optional.empty());
        when(memberRepository.findByProviderAndProviderId(Provider.LOCAL, LOGIN_ID))
                .thenReturn(Optional.of(member));

        assertInvalidCredentials(() -> authService.loginWithLocal("missing", "wrong"));
        assertInvalidCredentials(() -> authService.loginWithLocal(LOGIN_ID, "wrong"));
    }

    @Test
    void disabledFeatureReturnsNotFoundWithoutLookingUpAnAccount() {
        ReflectionTestUtils.setField(authService, "testLoginEnabled", false);

        assertThatThrownBy(() -> authService.loginWithLocal(LOGIN_ID, testPassword))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LOCAL_LOGIN_DISABLED));
        verify(memberRepository, never()).findByProviderAndProviderId(any(), any());
    }

    @Test
    void disabledFeatureAlsoBlocksRefreshForPreviouslyIssuedLocalTokens() {
        ReflectionTestUtils.setField(authService, "testLoginEnabled", false);
        Member member = localMember(passwordEncoder.encode(testPassword));
        RefreshToken saved = new RefreshToken(1L, "saved-refresh", LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByToken("saved-refresh")).thenReturn(Optional.of(saved));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.reissue("saved-refresh"))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LOCAL_LOGIN_DISABLED));
        verify(jwtTokenProvider, never()).createAccessToken(any());
    }

    private void assertInvalidCredentials(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_LOCAL_CREDENTIALS));
    }

    private Member localMember(String passwordHash) {
        Member member = Member.builder()
                .email("unit@ssoak.my")
                .nickname("테스트")
                .provider(Provider.LOCAL)
                .providerId(LOGIN_ID)
                .passwordHash(passwordHash)
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }
}
