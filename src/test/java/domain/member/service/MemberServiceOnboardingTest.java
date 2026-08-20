package domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import domain.inventory.InventoryRepository;
import domain.member.Member;
import domain.member.MemberRepository;
import domain.member.Provider;
import domain.member.Role;
import domain.member.SkinConcern;
import domain.member.SkinType;
import global.exception.CustomException;
import global.exception.ErrorCode;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceOnboardingTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private InventoryRepository inventoryRepository;

    private MemberService memberService;
    private Member member;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository, inventoryRepository);
        member = Member.builder()
                .email("onboarding@ssoak.my")
                .nickname("김멋사")
                .provider(Provider.LOCAL)
                .providerId("soak_onboarding")
                .role(Role.USER)
                .build();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
    }

    @Test
    void rejectsCompletionUntilEveryRequiredProfileAndInventoryValueExists() {
        assertThatThrownBy(() -> memberService.completeOnboarding(1L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ONBOARDING_REQUIREMENTS_NOT_MET));
    }

    @Test
    void marksCompletionOnlyAfterProfileAndInventoryAreReady() {
        member.updateSkinType(SkinType.OILY);
        member.updateSkinConcerns(Set.of(SkinConcern.ACNE));
        when(inventoryRepository.existsByMemberId(1L)).thenReturn(true);

        memberService.completeOnboarding(1L);

        assertThat(member.isOnboardingCompleted()).isTrue();
        assertThat(member.getOnboardingCompletedAt()).isNotNull();
    }
}
