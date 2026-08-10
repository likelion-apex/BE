package domain.member.controller;

import domain.auth.dto.response.MemberResponse;
import domain.member.MemberRepository;
import global.common.ApiResponse;
import global.exception.CustomException;
import global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Member", description = "회원 관련 API")
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberRepository memberRepository;

    @Operation(summary = "내 정보 조회", description = "Access Token으로 인증된 회원의 기본 정보를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ApiResponse<MemberResponse> getMyInfo(@AuthenticationPrincipal Long memberId) {
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        return ApiResponse.success(MemberResponse.from(member));
    }
}
