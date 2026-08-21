package domain.routine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import domain.beauty.shortform.application.ShortformAnalysisJsonMapper;
import domain.beauty.shortform.application.ShortformAnalysisSnapshotNormalizer;
import domain.beauty.shortform.application.ShortformRoutineTypeResolver;
import domain.beauty.shortform.domain.ShortformAnalysisRepository;
import domain.ingredient.dto.response.SkinAnalysisResponse;
import domain.ingredient.service.ProductCompatibilityService;
import domain.ingredient.service.SkinAnalysisService;
import domain.inventory.Inventory;
import domain.inventory.InventoryRepository;
import domain.inventory.Product;
import domain.inventory.ProductCategory;
import domain.member.Member;
import domain.member.MemberRepository;
import domain.member.Provider;
import domain.member.Role;
import domain.routine.domain.RoutineType;
import domain.routine.dto.response.RoutineGenerationResponse;
import domain.routine.repository.DailyConditionRepository;
import domain.routine.repository.RoutineLogRepository;
import domain.routine.repository.RoutineLogStepRepository;
import domain.routine.repository.RoutineRepository;
import global.util.PublicUrlResolver;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoutineServiceGenerateRoutineTest {

    @Mock
    private ShortformRoutineTypeResolver routineTypeResolver;
    @Mock
    private RoutineRepository routineRepository;
    @Mock
    private RoutineLogRepository routineLogRepository;
    @Mock
    private RoutineLogStepRepository routineLogStepRepository;
    @Mock
    private DailyConditionRepository dailyConditionRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private SkinAnalysisService skinAnalysisService;
    @Mock
    private ProductCompatibilityService productCompatibilityService;
    @Mock
    private ShortformAnalysisRepository shortformAnalysisRepository;
    @Mock
    private ShortformAnalysisJsonMapper shortformAnalysisJsonMapper;
    @Mock
    private ShortformAnalysisSnapshotNormalizer shortformAnalysisSnapshotNormalizer;

    private RoutineService routineService;
    private Member member;

    @BeforeEach
    void setUp() {
        routineService = new RoutineService(
                routineTypeResolver,
                routineRepository,
                routineLogRepository,
                routineLogStepRepository,
                dailyConditionRepository,
                memberRepository,
                inventoryRepository,
                skinAnalysisService,
                productCompatibilityService,
                shortformAnalysisRepository,
                shortformAnalysisJsonMapper,
                shortformAnalysisSnapshotNormalizer,
                new PublicUrlResolver(""));
        member = Member.builder()
                .nickname("테스터")
                .provider(Provider.KAKAO)
                .providerId("routine-etc-test")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(member, "id", 9L);
    }

    @Test
    void generateRoutineExcludesEtcCategoryFromSteps() {
        Inventory cream = inventory(11L, 3L, "수분 크림", ProductCategory.CREAM);
        Inventory sunscreen = inventory(12L, 4L, "수분 선크림", ProductCategory.ETC);
        when(inventoryRepository.findAllByMemberIdOrderByCreatedAtDesc(9L))
                .thenReturn(List.of(cream, sunscreen));
        when(skinAnalysisService.analyze(9L, 3L)).thenReturn(analysis(3L, "수분 크림", 88));

        RoutineGenerationResponse response = routineService.generateRoutine(9L, RoutineType.DAY);

        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().get(0).inventoryId()).isEqualTo(11L);
        assertThat(response.steps().get(0).category()).isEqualTo(ProductCategory.CREAM);
        verify(skinAnalysisService).analyze(9L, 3L);
        verify(skinAnalysisService, never()).analyze(9L, 4L);
        verify(productCompatibilityService, never()).compare(any());
    }

    private Inventory inventory(long inventoryId, long productId, String name, ProductCategory category) {
        Product product = Product.builder().name(name).category(category).build();
        ReflectionTestUtils.setField(product, "id", productId);
        Inventory inventory = Inventory.builder().member(member).product(product).build();
        ReflectionTestUtils.setField(inventory, "id", inventoryId);
        return inventory;
    }

    private SkinAnalysisResponse analysis(long productId, String productName, int score) {
        return new SkinAnalysisResponse(
                productId, productName, score, "안전", null, null, LocalDateTime.now());
    }
}
