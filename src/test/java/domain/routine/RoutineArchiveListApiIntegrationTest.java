import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import domain.member.Member;
import domain.member.MemberRepository;
import domain.member.Provider;
import domain.member.Role;
import domain.routine.domain.Routine;
import domain.routine.domain.RoutineStatus;
import domain.routine.domain.RoutineStep;
import domain.routine.domain.RoutineType;
import domain.routine.repository.RoutineRepository;
import domain.beauty.shortform.domain.RoutineSaveType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ApexBeApplication.class)
@AutoConfigureMockMvc
class RoutineArchiveListApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RoutineRepository routineRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member member;

    @BeforeEach
    void setUp() {
        routineRepository.deleteAll();
        memberRepository.deleteAll();
        member = memberRepository.saveAndFlush(Member.builder()
                .nickname("테스터")
                .provider(Provider.KAKAO)
                .providerId("archive-list-member")
                .role(Role.USER)
                .build());
    }

    @Test
    void filtersByYearAndSortsByName() throws Exception {
        saveArchivedRoutine("다라 루틴", LocalDateTime.of(2026, 3, 1, 10, 0), 1);
        saveArchivedRoutine("가나 루틴", LocalDateTime.of(2026, 6, 1, 10, 0), 1);
        saveArchivedRoutine("나다 루틴", LocalDateTime.of(2025, 12, 31, 23, 59), 1); // 2025 -> 제외되어야 함

        mockMvc.perform(get("/api/v1/routines")
                        .with(authentication(memberAuthentication(member.getId())))
                        .param("status", "ARCHIVED")
                        .param("year", "2026")
                        .param("sort", "NAME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.routines[0].name").value("가나 루틴"))
                .andExpect(jsonPath("$.data.routines[1].name").value("다라 루틴"));
    }

    @Test
    void omittingYearFiltersToRecentThreeYearsAndSortsByStepCount() throws Exception {
        saveArchivedRoutine("스텝2", LocalDateTime.now().minusDays(1), 2);
        saveArchivedRoutine("스텝0", LocalDateTime.now().minusDays(2), 0);
        saveArchivedRoutine("스텝1", LocalDateTime.now().minusDays(3), 1);
        saveArchivedRoutine("오래된 루틴", LocalDateTime.now().minusYears(4), 3); // 3년 초과 -> 제외되어야 함

        mockMvc.perform(get("/api/v1/routines")
                        .with(authentication(memberAuthentication(member.getId())))
                        .param("status", "ARCHIVED")
                        .param("sort", "STEP_COUNT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.routines[0].name").value("스텝0"))
                .andExpect(jsonPath("$.data.routines[1].name").value("스텝1"))
                .andExpect(jsonPath("$.data.routines[2].name").value("스텝2"));
    }

    private void saveArchivedRoutine(String name, LocalDateTime createdAt, int stepCount) {
        Routine routine = new Routine(member, null, name, RoutineType.DAY, RoutineStatus.ARCHIVED, RoutineSaveType.LIBRARY);
        for (int i = 0; i < stepCount; i++) {
            routine.addStep(new RoutineStep(routine, null, null, i + 1, "제품" + i, null, "CLEANSER", null, null));
        }
        Routine saved = routineRepository.saveAndFlush(routine);
        jdbcTemplate.update("UPDATE routines SET created_at = ? WHERE id = ?", createdAt, saved.getId());
    }

    private UsernamePasswordAuthenticationToken memberAuthentication(Long memberId) {
        return new UsernamePasswordAuthenticationToken(
                memberId, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
