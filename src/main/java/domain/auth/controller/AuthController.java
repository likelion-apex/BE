package domain.auth.controller;

import domain.auth.dto.request.KakaoLoginRequest;
import domain.auth.dto.request.ReissueTokenRequest;
import domain.auth.dto.response.TokenResponse;
import domain.auth.service.AuthService;
import global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
                    카카오에서 로그인 인가 후 전달받은 authorization code를 이용해,
                    카카오 사용자 정보를 조회한 뒤 회원가입 또는 로그인 처리를 하고 인증에 사용할 JWT를 발급합니다.

                    카카오 토큰 교환 시 사용하는 redirect_uri는 인가 코드를 발급받을 때 사용한 값과 정확히 일치해야 합니다.
                    프론트엔드 배포 환경(로컬/운영 등)의 redirect_uri가 서버 기본값(kakao.redirect-uri)과 다르면,
                    요청 본문의 redirectUri에 실제로 사용한 값을 반드시 함께 보내야 합니다. 그렇지 않으면 카카오가
                    redirect_uri 불일치로 토큰 발급을 거절하여 502(AUTH-009)가 발생합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 또는 회원가입 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "인가 코드 누락",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success":false,"code":"COMMON-400","message":"code: 인가 코드(code)는 필수입니다."}
                                    """))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "카카오 토큰 발급 또는 사용자 정보 조회 실패 (주로 redirect_uri 불일치)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success":false,"code":"AUTH-009","message":"카카오 토큰 발급 요청에 실패했습니다."}
                                    """))
            )
    })
    @PostMapping("/kakao/login")
    public ApiResponse<TokenResponse> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return ApiResponse.success(
                "카카오 로그인이 완료되었습니다.",
                authService.loginWithKakao(request.code(), request.redirectUri()));
    }

    @Operation(summary = "Access/Refresh 토큰 재발급", description = "전달받은 refresh token으로 access/refresh 토큰을 재발급합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 재발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "refresh token 누락",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않거나 저장되지 않은 refresh token",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@Valid @RequestBody ReissueTokenRequest request) {
        return ApiResponse.success("토큰이 재발급되었습니다.", authService.reissue(request.refreshToken()));
    }

    @Operation(summary = "로그아웃", description = "Access Token으로 인증된 회원의 refresh token을 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token 누락 또는 오류",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Long memberId) {
        authService.logout(memberId);
        return ApiResponse.success("로그아웃되었습니다.", null);
    }
}
