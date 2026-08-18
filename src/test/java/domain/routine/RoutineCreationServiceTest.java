package domain.routine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import domain.beauty.shortform.domain.RoutineOptimizationSnapshot;
import domain.beauty.shortform.domain.RoutineSaveType;
import domain.beauty.shortform.domain.ShortformAnalysis;
import domain.beauty.shortform.domain.ShortformAnalysisRepository;
import domain.beauty.shortform.domain.ShortformAnalysisSnapshot;
import domain.inventory.InventoryRepository;
import domain.inventory.ProductRepository;
import domain.member.Member;
import domain.member.Provider;
import domain.member.Role;
import java.util.List;
import java.util.Optional;

import domain.routine.domain.Routine;
import domain.routine.domain.RoutineLog;
import domain.routine.domain.RoutineStatus;
import domain.routine.domain.RoutineType;
import domain.routine.repository.RoutineLogRepository;
import domain.routine.repository.RoutineRepository;
import domain.routine.service.RoutineCreationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoutineCreationServiceTest {

    @Mock
    private RoutineRepository routineRepository;
    @Mock
    private RoutineLogRepository routineLogRepository;
    @Mock
    private ShortformAnalysisRepository analysisRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private ProductRepository productRepository;

    private RoutineCreationService service;
    private Member member;
    private ShortformAnalysis analysis;

    @BeforeEach
    void setUp() {
        service = new RoutineCreationService(
                routineRepository,
                routineLogRepository,
                analysisRepository,
                inventoryRepository,
                productRepository
        );
        member = Member.builder()
                .nickname("테스터")
                .provider(Provider.KAKAO)
                .providerId("routine-type-test")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(member, "id", 1L);
        analysis = new ShortformAnalysis(
                member,
                "video-id",
                "https://www.youtube.com/watch?v=video-id",
                "fingerprint"
        );
        ReflectionTestUtils.setField(analysis, "id", 10L);
    }

    @Test
    void createsTodayRoutineWithinRequestedType() {
        when(routineRepository.findByMemberIdAndSourceAnalysisIdAndSaveTypeAndRoutineType(
                1L, 10L, RoutineSaveType.TODAY, RoutineType.DAY))
                .thenReturn(Optional.empty());
        when(analysisRepository.findByIdAndMemberId(10L, 1L)).thenReturn(Optional.of(analysis));
        when(routineRepository.existsByMemberIdAndStatusAndRoutineType(
                1L, RoutineStatus.ACTIVE, RoutineType.DAY)).thenReturn(false);
        when(routineRepository.saveAndFlush(any(Routine.class))).thenAnswer(invocation -> {
            Routine routine = invocation.getArgument(0);
            ReflectionTestUtils.setField(routine, "id", 20L);
            return routine;
        });

        RoutineCreationService.RoutineApplyResult result = service.create(
                1L,
                10L,
                RoutineSaveType.TODAY,
                RoutineType.DAY,
                analysisSnapshot(),
                emptyOptimization()
        );

        ArgumentCaptor<Routine> routineCaptor = ArgumentCaptor.forClass(Routine.class);
        verify(routineRepository).saveAndFlush(routineCaptor.capture());
        assertThat(routineCaptor.getValue().getRoutineType()).isEqualTo(RoutineType.DAY);
        assertThat(result.routineType()).isEqualTo(RoutineType.DAY);
        assertThat(result.status()).isEqualTo(RoutineStatus.ACTIVE);
        verify(routineLogRepository).save(any(RoutineLog.class));
    }

    @Test
    void reusesOnlyRoutineWithSameSaveTypeAndRoutineType() {
        Routine existing = new Routine(
                member,
                analysis,
                "기존 나이트 루틴",
                RoutineType.NIGHT,
                RoutineStatus.ARCHIVED,
                RoutineSaveType.LIBRARY
        );
        ReflectionTestUtils.setField(existing, "id", 30L);
        when(routineRepository.findByMemberIdAndSourceAnalysisIdAndSaveTypeAndRoutineType(
                1L, 10L, RoutineSaveType.LIBRARY, RoutineType.NIGHT))
                .thenReturn(Optional.of(existing));

        RoutineCreationService.RoutineApplyResult result = service.create(
                1L,
                10L,
                RoutineSaveType.LIBRARY,
                RoutineType.NIGHT,
                analysisSnapshot(),
                emptyOptimization()
        );

        assertThat(result.reused()).isTrue();
        assertThat(result.routineId()).isEqualTo(30L);
        assertThat(result.routineType()).isEqualTo(RoutineType.NIGHT);
        verify(analysisRepository, never()).findByIdAndMemberId(any(), any());
        verify(routineRepository, never()).saveAndFlush(any());
    }

    private ShortformAnalysisSnapshot analysisSnapshot() {
        return new ShortformAnalysisSnapshot(
                "test",
                "video-id",
                "https://www.youtube.com/watch?v=video-id",
                "테스트 루틴",
                "스킨케어",
                80,
                List.of(),
                "보습",
                "수분 조합",
                "요약",
                List.of(),
                "안내",
                List.of(),
                null
        );
    }

    private RoutineOptimizationSnapshot emptyOptimization() {
        return new RoutineOptimizationSnapshot(0, 0, 0, "최적화 완료", List.of());
    }
}
