package feat.auth.service;

import feat.auth.dto.response.KakaoTokenResponse;
import feat.auth.dto.response.KakaoUserInfoResponse;
import feat.common.exception.CustomException;
import feat.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Exchanges the Kakao authorization code (obtained by the SPA frontend) for a
 * Kakao access token and fetches the associated Kakao user profile.
 */
@Component
public class KakaoOAuthClient {

    private final RestClient restClient;

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String tokenUri;
    private final String userInfoUri;

    public KakaoOAuthClient(
            RestClient.Builder restClientBuilder,
            @Value("${kakao.client-id}") String clientId,
            @Value("${kakao.client-secret}") String clientSecret,
            @Value("${kakao.redirect-uri}") String redirectUri,
            @Value("${kakao.token-uri}") String tokenUri,
            @Value("${kakao.user-info-uri}") String userInfoUri
    ) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.tokenUri = tokenUri;
        this.userInfoUri = userInfoUri;
    }

    public KakaoTokenResponse requestToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (StringUtils.hasText(clientSecret)) {
            form.add("client_secret", clientSecret);
        }

        try {
            return restClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KakaoTokenResponse.class);
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.KAKAO_TOKEN_REQUEST_FAILED, e.getMessage());
        }
    }

    public KakaoUserInfoResponse requestUserInfo(String kakaoAccessToken) {
        try {
            return restClient.get()
                    .uri(userInfoUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(KakaoUserInfoResponse.class);
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.KAKAO_USER_INFO_REQUEST_FAILED, e.getMessage());
        }
    }
}
