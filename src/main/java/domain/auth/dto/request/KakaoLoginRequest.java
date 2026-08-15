package domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "카카오 로그인 요청")
public record KakaoLoginRequest(

        @Schema(description = "카카오에서 로그인 인가 후 리다이렉트로 전달받은 authorization code", example = "abcd1234...")
        @NotBlank(message = "인가 코드(code)는 필수입니다.")
        String code,

        @Schema(
                description = "프론트엔드가 카카오 인가 요청 시 사용한 redirect_uri. 카카오 토큰 발급 API는 인가 코드 발급 때와 "
                        + "동일한 redirect_uri로만 토큰 교환을 허용하므로, 프론트엔드 배포 환경(로컬/운영 등)이 서버 기본값과 다를 경우 "
                        + "반드시 실제로 사용한 값을 그대로 보내야 합니다. 생략하면 서버에 설정된 기본값(kakao.redirect-uri)을 사용합니다.",
                example = "https://likelion-apex-fe.vercel.app/onboarding/kakaocallback"
        )
        String redirectUri
) {
}
