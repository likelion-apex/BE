package domain.member.controller;

import domain.auth.dto.response.MemberResponse;
import domain.member.dto.request.NicknameUpdateRequest;
import domain.member.dto.request.ProfileUpdateRequest;
import domain.member.dto.request.SkinConcernsUpdateRequest;
import domain.member.dto.request.SkinTypeUpdateRequest;
import domain.member.service.MemberService;
import global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Member", description = "회원 정보 및 프로필 관련 API")
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "내 정보 조회", description = "Access Token으로 인증된 회원의 기본 정보와 피부타입/피부고민을 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<MemberResponse> getMyInfo(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(memberService.getMyInfo(memberId));
    }

    @Operation(summary = "닉네임 변경", description = "서비스에서 사용할 닉네임을 변경합니다 (최대 10자). 온보딩과 마이페이지에서 공통으로 사용합니다.")
    @PatchMapping("/me/nickname")
    public ApiResponse<MemberResponse> updateNickname(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody NicknameUpdateRequest request
    ) {
        return ApiResponse.success("닉네임이 변경되었습니다.", memberService.updateNickname(memberId, request.nickname()));
    }

    @Operation(summary = "피부 타입 변경", description = "건성/중성/지성/복합성/수부지 중 하나를 단일 선택으로 저장합니다.")
    @PatchMapping("/me/skin-type")
    public ApiResponse<MemberResponse> updateSkinType(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody SkinTypeUpdateRequest request
    ) {
        return ApiResponse.success("피부 타입이 변경되었습니다.", memberService.updateSkinType(memberId, request.skinType()));
    }

    @Operation(summary = "피부 고민 변경", description = "속건조/여드름/민감성/미백·잡티/다크서클/색소·블랙헤드/홍조/아토피 중 다중 선택한 목록으로 전체 교체합니다.")
    @PatchMapping("/me/skin-concerns")
    public ApiResponse<MemberResponse> updateSkinConcerns(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody SkinConcernsUpdateRequest request
    ) {
        return ApiResponse.success(
                "피부 고민이 변경되었습니다.",
                memberService.updateSkinConcerns(memberId, Set.copyOf(request.skinConcerns()))
        );
    }

    @Operation(
            summary = "프로필 통합 수정",
            description = "마이페이지의 \"프로필 수정\" 화면에서 닉네임/피부타입/피부고민을 한 번에 수정합니다."
    )
    @PatchMapping("/me")
    public ApiResponse<MemberResponse> updateProfile(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        return ApiResponse.success("프로필이 수정되었습니다.", memberService.updateProfile(memberId, request));
    }
}
