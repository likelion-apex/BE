package domain.member;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데모/테스트용 회원 시딩. members 테이블이 비어있을 때만 1건 생성해
 * 재시작/재배포 시 중복 삽입을 막는다. 운영 DB 오염을 막기 위해 local 프로필에서만 동작한다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DemoMemberSeeder implements ApplicationRunner {

    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (memberRepository.count() > 0) {
            log.info("회원 데이터가 이미 존재하여 데모 회원 시딩을 건너뜁니다.");
            return;
        }

        Member member = Member.builder()
                .email("demo@apex.dev")
                .nickname("데모유저")
                .provider(Provider.KAKAO)
                .providerId("demo-provider-id")
                .role(Role.USER)
                .build();
        member.updateSkinType(SkinType.DRY);
        member.updateSkinConcerns(Set.of(SkinConcern.DRYNESS, SkinConcern.SENSITIVE));

        Member saved = memberRepository.save(member);
        log.info("데모 회원 시딩 완료: id={}, skinType={}, skinConcerns={}",
                saved.getId(), saved.getSkinType(), saved.getSkinConcerns());
    }
}