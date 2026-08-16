package domain.routine;

import domain.beauty.shortform.domain.RoutineSaveType;
import domain.member.Member;
import domain.member.MemberRepository;
import domain.routine.domain.Routine;
import domain.routine.domain.RoutineStatus;
import domain.routine.domain.RoutineStep;
import domain.routine.domain.RoutineType;
import domain.routine.repository.RoutineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Routine API(6.x) 로컬 검증용 데모 데이터. DAY/NIGHT 활성 루틴을 각각 하나씩 만들어
 * 현재 시각과 무관하게 6.1 조회가 항상 뭔가를 반환하도록 한다.
 * IngredientDataSeeder(1)/DemoMemberSeeder(2)/DemoInventorySeeder(3) 이후에 실행되어야 한다.
 */
@Slf4j
@Component
@Profile("local")
@Order(4)
@RequiredArgsConstructor
public class DemoRoutineSeeder implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final RoutineRepository routineRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (routineRepository.count() > 0) {
            log.info("루틴 데모 데이터가 이미 존재하여 시딩을 건너뜁니다.");
            return;
        }

        memberRepository.findAll().stream().findFirst().ifPresent(member -> {
            seedRoutine(member, RoutineType.DAY, "아침 스킨케어 루틴");
            seedRoutine(member, RoutineType.NIGHT, "저녁 스킨케어 루틴");
            log.info("루틴 데모 데이터 시딩 완료: memberId={}", member.getId());
        });
    }

    private void seedRoutine(Member member, RoutineType routineType, String name) {
        Routine routine = new Routine(member, null, name, routineType, RoutineStatus.ACTIVE, RoutineSaveType.TODAY);
        routine.addStep(new RoutineStep(routine, null, null, 1, "저자극 폼클렌저", "데모브랜드", "클렌저", null, null));
        routine.addStep(new RoutineStep(routine, null, null, 2, "수분 토너", "데모브랜드", "SKIN_TONER", null, null));
        routine.addStep(new RoutineStep(routine, null, null, 3, "수분 크림", "데모브랜드", "CREAM", null, null));
        routineRepository.save(routine);
    }
}