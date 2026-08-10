package feat.auth.controller;

import feat.auth.dto.request.KakaoLoginRequest;
import feat.auth.dto.request.ReissueTokenRequest;
import feat.auth.dto.response.TokenResponse;
import feat.auth.service.AuthService;
import feat.common.ApiResponse;
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

@Tag(name = "Auth", description = "??? ?? ??? ? ?? ?? API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "??? ???/????",
            description = """
                    ?????? ??? ?? ????? ???? authorization code? ????,
                    ???? ??? ?? ?? ? ??? ?? ?? ? ??? ????? ?? ????? JWT? ?????.
                    """
    )
    @PostMapping("/kakao/login")
    public ApiResponse<TokenResponse> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return ApiResponse.success("??? ???? ??????.", authService.loginWithKakao(request.code()));
    }

    @Operation(summary = "Access/Refresh ?? ???", description = "??? refresh token?? access/refresh ??? ??????.")
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@Valid @RequestBody ReissueTokenRequest request) {
        return ApiResponse.success("??? ????????.", authService.reissue(request.refreshToken()));
    }

    @Operation(summary = "????", description = "Access Token?? ?? ? ??? refresh token? ?????.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Long memberId) {
        authService.logout(memberId);
        return ApiResponse.success("?????????.", null);
    }
}
