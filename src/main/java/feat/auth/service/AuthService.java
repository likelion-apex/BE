package feat.auth.service;

import feat.auth.dto.response.KakaoTokenResponse;
import feat.auth.dto.response.KakaoUserInfoResponse;
import feat.auth.dto.response.MemberResponse;
import feat.auth.dto.response.TokenResponse;
import feat.common.exception.CustomException;
import feat.common.exception.ErrorCode;
import feat.domain.auth.RefreshToken;
import feat.domain.auth.RefreshTokenRepository;
import feat.domain.member.Member;
import feat.domain.member.MemberRepository;
import feat.domain.member.Provider;
import feat.domain.member.Role;
import feat.security.jwt.JwtTokenProvider;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private static final String DEFAULT_NICKNAME = "??????";

    private final KakaoOAuthClient kakaoOAuthClient;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public TokenResponse loginWithKakao(String code) {
        KakaoTokenResponse kakaoToken = kakaoOAuthClient.requestToken(code);
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

        String newAccessToken = jwtTokenProvider.createAccessToken(member);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(member);
        savedRefreshToken.rotate(newRefreshToken, toLocalDateTime(jwtTokenProvider.getRefreshTokenExpiration()));

        return TokenResponse.of(
                newAccessToken,
                newRefreshToken,
                jwtTokenProvider.getAccessTokenExpiration(),
                false,
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
