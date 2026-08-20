package domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class TestLoginAccountSeederTest {

    @Mock
    private MemberRepository memberRepository;

    @Test
    void createsExactlySixIndependentIncompleteLocalAccounts() throws Exception {
        when(memberRepository.findByProviderAndProviderId(any(), any())).thenReturn(Optional.empty());
        TestLoginAccountSeeder seeder = new TestLoginAccountSeeder(memberRepository);

        seeder.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository, org.mockito.Mockito.times(6)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(member -> {
            assertThat(member.getProvider()).isEqualTo(Provider.LOCAL);
            assertThat(member.getRole()).isEqualTo(Role.USER);
            assertThat(member.getProfileImageUrl()).isNull();
            assertThat(member.getSkinType()).isNull();
            assertThat(member.getSkinConcerns()).isEmpty();
            assertThat(member.isOnboardingCompleted()).isFalse();
            assertThat(member.getPasswordHash()).startsWith("$2y$12$");
        });
        assertThat(captor.getAllValues()).extracting(Member::getProviderId)
                .containsExactlyElementsOf(TestLoginAccountSeeder.loginIds());
    }

    @Test
    void rerunKeepsExistingProfileAndOnboardingData() throws Exception {
        Member existing = Member.builder()
                .email("existing@ssoak.my")
                .nickname("변경한닉네임")
                .provider(Provider.LOCAL)
                .providerId("existing")
                .passwordHash("old-hash")
                .role(Role.USER)
                .build();
        existing.updateSkinType(SkinType.DRY);
        existing.updateSkinConcerns(Set.of(SkinConcern.DRYNESS));
        existing.completeOnboarding();
        when(memberRepository.findByProviderAndProviderId(Provider.LOCAL, "soak_judge"))
                .thenReturn(Optional.of(existing));
        when(memberRepository.findByProviderAndProviderId(Provider.LOCAL, "soak_test01"))
                .thenReturn(Optional.of(existing));
        when(memberRepository.findByProviderAndProviderId(Provider.LOCAL, "soak_test02"))
                .thenReturn(Optional.of(existing));
        when(memberRepository.findByProviderAndProviderId(Provider.LOCAL, "soak_test03"))
                .thenReturn(Optional.of(existing));
        when(memberRepository.findByProviderAndProviderId(Provider.LOCAL, "soak_test04"))
                .thenReturn(Optional.of(existing));
        when(memberRepository.findByProviderAndProviderId(Provider.LOCAL, "soak_test05"))
                .thenReturn(Optional.of(existing));
        TestLoginAccountSeeder seeder = new TestLoginAccountSeeder(memberRepository);

        seeder.run(new DefaultApplicationArguments(new String[0]));

        verify(memberRepository, never()).save(any());
        assertThat(existing.getNickname()).isEqualTo("변경한닉네임");
        assertThat(existing.getSkinType()).isEqualTo(SkinType.DRY);
        assertThat(existing.getSkinConcerns()).containsExactly(SkinConcern.DRYNESS);
        assertThat(existing.isOnboardingCompleted()).isTrue();
    }
}
