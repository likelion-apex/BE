package domain.member;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds only the six pre-issued hackathon accounts when shared-account login is enabled. */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "test-login.enabled", havingValue = "true")
public class TestLoginAccountSeeder implements ApplicationRunner {

    private static final List<TestAccount> ACCOUNTS = List.of(
            new TestAccount(
                    "soak_judge",
                    "judge@ssoak.my",
                    "$2y$12$1GpLFnuFuB2rBPf0u2bT1.sTnvpwD2NmjjNVZlXRd7GOQE1sskFsu"
            ),
            new TestAccount(
                    "soak_test01",
                    "test01@ssoak.my",
                    "$2y$12$eDodcxGRdS1L5BfRYU3U7eeoJjpoy6u.D2vjxX2KjJ7AwL/Aep9aq"
            ),
            new TestAccount(
                    "soak_test02",
                    "test02@ssoak.my",
                    "$2y$12$eT6V9CvUSPEH3fcZb5yNwuw049tS9Hy2Sto8UxrwAdc.BQQ7MpoSK"
            ),
            new TestAccount(
                    "soak_test03",
                    "test03@ssoak.my",
                    "$2y$12$NjJMscNnjJOXU4OgpocaIecncKKumIolxc5HmZS7yfJkpMtaTIyMe"
            ),
            new TestAccount(
                    "soak_test04",
                    "test04@ssoak.my",
                    "$2y$12$MsZLf0FU7bga4qVKABVcFOj.77MiNorLCG8uEMeetPYS5cMVdbkwW"
            ),
            new TestAccount(
                    "soak_test05",
                    "test05@ssoak.my",
                    "$2y$12$wrnkyj7pv.RaJnoEkk8Tmua/O/.dTwR7jUWW3.yJmwmDyAB9rD7z."
            )
    );

    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        for (TestAccount account : ACCOUNTS) {
            var existing = memberRepository.findByProviderAndProviderId(Provider.LOCAL, account.loginId());
            if (existing.isPresent()) {
                existing.get().updatePasswordHash(account.passwordHash());
                continue;
            }

            memberRepository.save(Member.builder()
                    .email(account.email())
                    .nickname(null)
                    .profileImageUrl(null)
                    .provider(Provider.LOCAL)
                    .providerId(account.loginId())
                    .passwordHash(account.passwordHash())
                    .role(Role.USER)
                    .build());
            created++;
        }
        log.info("심사용 ID/PW 계정 시딩 완료: 생성={}건, 유지={}건", created, ACCOUNTS.size() - created);
    }

    static List<String> loginIds() {
        return ACCOUNTS.stream().map(TestAccount::loginId).toList();
    }

    private record TestAccount(String loginId, String email, String passwordHash) {
    }
}
