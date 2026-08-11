package feat.domain.member.service;

import feat.auth.dto.response.MemberResponse;
import feat.common.exception.CustomException;
import feat.common.exception.ErrorCode;
import feat.domain.member.Member;
import feat.domain.member.MemberRepository;
import feat.domain.member.SkinConcern;
import feat.domain.member.SkinType;
import feat.domain.member.dto.request.ProfileUpdateRequest;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public MemberResponse getMyInfo(Long memberId) {
        return MemberResponse.from(findMember(memberId));
    }

    public MemberResponse updateNickname(Long memberId, String nickname) {
        Member member = findMember(memberId);
        member.updateNickname(nickname);
        return MemberResponse.from(member);
    }

    public MemberResponse updateSkinType(Long memberId, SkinType skinType) {
        Member member = findMember(memberId);
        member.updateSkinType(skinType);
        return MemberResponse.from(member);
    }

    public MemberResponse updateSkinConcerns(Long memberId, Set<SkinConcern> skinConcerns) {
        Member member = findMember(memberId);
        member.updateSkinConcerns(skinConcerns);
        return MemberResponse.from(member);
    }

    public MemberResponse updateProfile(Long memberId, ProfileUpdateRequest request) {
        Member member = findMember(memberId);
        member.updateNickname(request.nickname());
        member.updateSkinType(request.skinType());
        member.updateSkinConcerns(Set.copyOf(request.skinConcerns()));
        return MemberResponse.from(member);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
