package feat.apex_BE.auth.controller;

import feat.apex_BE.auth.dto.request.KakaoLoginRequest;
import feat.apex_BE.auth.dto.request.ReissueTokenRequest;
import feat.apex_BE.auth.dto.response.TokenResponse;
import feat.apex_BE.auth.service.AuthService;
import feat.apex_BE.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "카카오 소셜 로그인 및 토큰 관리 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "카카오 로그인/회원가입",
            description = """
                    프론트엔드가 카카오 인가 서버로부터 전달받은 authorization code를 전달하면,
                    백엔드가 카카오 토큰 교환 및 사용자 정보 조회 후 회원을 조회하거나 신규 가입시키고 JWT를 발급합니다.
                    """
    )
    @PostMapping("/kakao/login")
    public ApiResponse<TokenResponse> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return ApiResponse.success("카카오 로그인에 성공했습니다.", authService.loginWithKakao(request.code()));
    }

    @Operation(summary = "Access/Refresh 토큰 재발급", description = "유효한 refresh token으로 access/refresh 토큰을 재발급합니다.")
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@Valid @RequestBody ReissueTokenRequest request) {
        return ApiResponse.success("토큰이 재발급되었습니다.", authService.reissue(request.refreshToken()));
    }

    @Operation(summary = "로그아웃", description = "Access Token으로 인증 후 저장된 refresh token을 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Long memberId) {
        authService.logout(memberId);
        return ApiResponse.success("로그아웃되었습니다.", null);
    }
}
