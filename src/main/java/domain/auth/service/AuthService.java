package domain.auth.service;

import domain.auth.dto.response.KakaoTokenResponse;
import domain.auth.dto.response.KakaoUserInfoResponse;
import domain.auth.dto.response.MemberResponse;
import domain.auth.dto.response.TokenResponse;
import domain.auth.RefreshToken;
import domain.auth.RefreshTokenRepository;
import domain.member.Member;
import domain.member.MemberRepository;
import domain.member.Provider;
import domain.member.Role;
import global.exception.CustomException;
import global.exception.ErrorCode;
import security.jwt.JwtTokenProvider;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private static final String DEFAULT_NICKNAME = "카카오사용자";
    private static final String DUMMY_PASSWORD_HASH =
            "$2y$12$vAKP/IgjSttNfxBmgWKPqeF/qy6REPpQ5Mw.rD7PwWSQOJ8jW4X42";

    private final KakaoOAuthClient kakaoOAuthClient;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${test-login.enabled:false}")
    private boolean testLoginEnabled;

    public TokenResponse loginWithKakao(String code, String redirectUri) {
        KakaoTokenResponse kakaoToken = kakaoOAuthClient.requestToken(code, redirectUri);
        KakaoUserInfoResponse userInfo = kakaoOAuthClient.requestUserInfo(kakaoToken.accessToken());

        String providerId = String.valueOf(userInfo.id());
        String email = extractEmail(userInfo);
        String nickname = extractNickname(userInfo);
        String profileImageUrl = extractProfileImageUrl(userInfo);

        boolean isNewMember = memberRepository.findByProviderAndProviderId(Provider.KAKAO, providerId).isEmpty();

        Member member = memberRepository.findByProviderAndProviderId(Provider.KAKAO, providerId)
                .map(existing -> {
                    existing.updateProfile(nickname, profileImageUrl);
                    return existing;
                })
                .orElseGet(() -> memberRepository.save(Member.builder()
                        .email(email)
                        .nickname(nickname)
                        .profileImageUrl(profileImageUrl)
                        .provider(Provider.KAKAO)
                        .providerId(providerId)
                        .role(Role.USER)
                        .build()));

        return issueTokens(member, isNewMember);
    }

    public TokenResponse loginWithLocal(String loginId, String password) {
        if (!testLoginEnabled) {
            throw new CustomException(ErrorCode.LOCAL_LOGIN_DISABLED);
        }

        String normalizedLoginId = loginId.strip().toLowerCase(Locale.ROOT);
        var memberCandidate = memberRepository.findByProviderAndProviderId(Provider.LOCAL, normalizedLoginId);
        String passwordHash = memberCandidate.map(Member::getPasswordHash).orElse(DUMMY_PASSWORD_HASH);
        boolean passwordMatches = passwordEncoder.matches(password, passwordHash);

        if (memberCandidate.isEmpty() || !passwordMatches) {
            throw new CustomException(ErrorCode.INVALID_LOCAL_CREDENTIALS);
        }

        return issueTokens(memberCandidate.get(), false);
    }

    public TokenResponse reissue(String refreshTokenValue) {
        jwtTokenProvider.validateToken(refreshTokenValue);

        RefreshToken savedRefreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (savedRefreshToken.isExpired()) {
            refreshTokenRepository.delete(savedRefreshToken);
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        Member member = memberRepository.findById(savedRefreshToken.getMemberId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        if (member.getProvider() == Provider.LOCAL && !testLoginEnabled) {
            throw new CustomException(ErrorCode.LOCAL_LOGIN_DISABLED);
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(member);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(member);
        savedRefreshToken.rotate(newRefreshToken, toLocalDateTime(jwtTokenProvider.getRefreshTokenExpiration()));

        return TokenResponse.of(
                newAccessToken,
                newRefreshToken,
                jwtTokenProvider.getAccessTokenExpiration(),
                false,
                !member.isOnboardingCompleted(),
                MemberResponse.from(member)
        );
    }

    public void logout(Long memberId) {
        refreshTokenRepository.deleteByMemberId(memberId);
    }

    private TokenResponse issueTokens(Member member, boolean isNewMember) {
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String refreshToken = jwtTokenProvider.createRefreshToken(member);
        LocalDateTime expiryDate = toLocalDateTime(jwtTokenProvider.getRefreshTokenExpiration());

        refreshTokenRepository.findByMemberId(member.getId())
                .ifPresentOrElse(
                        existing -> existing.rotate(refreshToken, expiryDate),
                        () -> refreshTokenRepository.save(new RefreshToken(member.getId(), refreshToken, expiryDate))
                );

        return TokenResponse.of(
                accessToken,
                refreshToken,
                jwtTokenProvider.getAccessTokenExpiration(),
                isNewMember,
                !member.isOnboardingCompleted(),
                MemberResponse.from(member)
        );
    }

    private LocalDateTime toLocalDateTime(long expirationMillis) {
        return LocalDateTime.now(ZoneId.systemDefault()).plusNanos(expirationMillis * 1_000_000);
    }

    private String extractEmail(KakaoUserInfoResponse userInfo) {
        if (userInfo.kakaoAccount() == null) {
            return null;
        }
        return userInfo.kakaoAccount().email();
    }

    private String extractNickname(KakaoUserInfoResponse userInfo) {
        if (userInfo.kakaoAccount() != null
                && userInfo.kakaoAccount().profile() != null
                && StringUtils.hasText(userInfo.kakaoAccount().profile().nickname())) {
            return userInfo.kakaoAccount().profile().nickname();
        }
        return DEFAULT_NICKNAME;
    }

    private String extractProfileImageUrl(KakaoUserInfoResponse userInfo) {
        if (userInfo.kakaoAccount() == null || userInfo.kakaoAccount().profile() == null) {
            return null;
        }
        return userInfo.kakaoAccount().profile().profileImageUrl();
    }
}
