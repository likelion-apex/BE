package domain.ingredient;

import domain.ingredient.domain.Ingredient;
import domain.ingredient.domain.IngredientInteraction;
import domain.ingredient.domain.InteractionType;
import domain.ingredient.domain.ProductIngredient;
import domain.ingredient.repository.IngredientInteractionRepository;
import domain.ingredient.repository.IngredientRepository;
import domain.ingredient.repository.ProductIngredientRepository;
import domain.inventory.Product;
import domain.inventory.ProductCategory;
import domain.inventory.ProductRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개발/데모용 성분·제품·궁합 데이터 시딩. 서버 기동 시 1회 실행되며,
 * IngredientInteraction 테이블이 비어있을 때만 동작해 재시작/재배포 시 중복 삽입을 막는다.
 * 운영 DB 오염을 막기 위해 local 프로필에서만 동작한다.
 * 다른 데모 시더(회원/루틴/인벤토리)가 이 제품 데이터를 전제하므로 가장 먼저 실행되어야 한다.
 */
@Slf4j
@Component
@Profile("local")
@Order(1)
@RequiredArgsConstructor
public class IngredientDataSeeder implements ApplicationRunner {

    private final IngredientRepository ingredientRepository;
    private final ProductRepository productRepository;
    private final ProductIngredientRepository productIngredientRepository;
    private final IngredientInteractionRepository ingredientInteractionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (ingredientInteractionRepository.count() > 0) {
            log.info("성분 데모 데이터가 이미 존재하여 시딩을 건너뜁니다.");
            return;
        }

        Map<String, Ingredient> ingredients = seedIngredients();
        Map<String, Product> products = seedProducts();
        seedProductIngredients(ingredients, products);
        int interactionCount = seedInteractions(ingredients);

        log.info("성분 데모 데이터 시딩 완료: 성분 {}개, 제품 {}개, 궁합 규칙 {}개",
                ingredients.size(), products.size(), interactionCount);
    }

    private Map<String, Ingredient> seedIngredients() {
        record Seed(String name, String functionTag, String functionGroup, int ewgGrade) {
        }
        List<Seed> seeds = List.of(
                new Seed("레티놀", "레티놀", "안티에이징", 6),
                new Seed("나이아신아마이드", "나이아신아마이드", "미백/피지조절", 1),
                new Seed("비타민C", "비타민C", "미백/항산화", 3),
                new Seed("AHA", "AHA-BHA", "각질케어", 4),
                new Seed("BHA", "AHA-BHA", "각질케어", 4),
                new Seed("히알루론산", "히알루론산", "보습", 1),
                new Seed("판테놀", "판테놀", "진정/보습", 1),
                new Seed("세라마이드", "세라마이드", "장벽강화", 1),
                new Seed("아데노신", "아데노신", "주름개선", 2),
                new Seed("정제수", "베이스", "베이스", 1),
                new Seed("글리세린", "글리세린", "보습", 1),
                new Seed("알코올", "알코올", "수렴/베이스", 7),
                new Seed("향료", "향료", "첨가제", 7),
                new Seed("페녹시에탄올", "방부제", "방부제", 4),
                new Seed("징크옥사이드", "자외선차단", "자외선차단", 2)
        );

        Map<String, Ingredient> saved = new LinkedHashMap<>();
        for (Seed seed : seeds) {
            Ingredient ingredient = Ingredient.builder()
                    .name(seed.name())
                    .functionTag(seed.functionTag())
                    .functionGroup(seed.functionGroup())
                    .ewgGrade(seed.ewgGrade())
                    .build();
            saved.put(seed.name(), ingredientRepository.save(ingredient));
        }
        return saved;
    }

    private Map<String, Product> seedProducts() {
        record Seed(String name, ProductCategory category) {
        }
        List<Seed> seeds = List.of(
                new Seed("레티놀 나이트 크림", ProductCategory.CREAM),
                new Seed("비타민C 세럼", ProductCategory.SERUM),
                new Seed("AHA 필링 토너", ProductCategory.SKIN_TONER),
                new Seed("저자극 수분 크림", ProductCategory.CREAM),
                new Seed("판테놀 진정 앰플", ProductCategory.SERUM),
                new Seed("BHA 트러블 세럼", ProductCategory.SERUM)
        );

        Map<String, Product> saved = new LinkedHashMap<>();
        for (Seed seed : seeds) {
            Product product = Product.builder()
                    .name(seed.name())
                    .category(seed.category())
                    .build();
            saved.put(seed.name(), productRepository.save(product));
        }
        return saved;
    }

    private void seedProductIngredients(Map<String, Ingredient> ingredients, Map<String, Product> products) {
        Map<String, List<String>> productIngredientNames = new LinkedHashMap<>();
        productIngredientNames.put("레티놀 나이트 크림", List.of("레티놀", "세라마이드", "글리세린", "정제수"));
        productIngredientNames.put("비타민C 세럼", List.of("비타민C", "나이아신아마이드", "히알루론산", "정제수"));
        productIngredientNames.put("AHA 필링 토너", List.of("AHA", "정제수", "글리세린", "알코올"));
        productIngredientNames.put("저자극 수분 크림", List.of("히알루론산", "판테놀", "세라마이드", "글리세린", "정제수"));
        productIngredientNames.put("판테놀 진정 앰플", List.of("판테놀", "히알루론산", "세라마이드", "정제수"));
        productIngredientNames.put("BHA 트러블 세럼", List.of("BHA", "나이아신아마이드", "알코올", "향료", "정제수"));

        productIngredientNames.forEach((productName, ingredientNames) -> {
            Product product = products.get(productName);
            int rank = 1;
            for (String ingredientName : ingredientNames) {
                productIngredientRepository.save(ProductIngredient.builder()
                        .product(product)
                        .ingredient(ingredients.get(ingredientName))
                        .rank(rank++)
                        .build());
            }
        });
    }

    private int seedInteractions(Map<String, Ingredient> ingredients) {
        record Seed(String nameA, String nameB, InteractionType type, String description) {
        }
        List<Seed> seeds = List.of(
                new Seed("레티놀", "AHA", InteractionType.CONFLICT,
                        "두 성분을 함께 사용하면 각질층이 얇아져 자극과 홍조가 심해질 수 있어요."),
                new Seed("레티놀", "BHA", InteractionType.CONFLICT,
                        "각질 제거 효과가 중첩되어 피부 장벽 손상 위험이 커져요."),
                new Seed("비타민C", "AHA", InteractionType.CONFLICT,
                        "산성도가 겹치면서 자극이 커지고 비타민C가 불안정해질 수 있어요."),
                new Seed("AHA", "BHA", InteractionType.CONFLICT,
                        "두 각질케어 성분을 동시에 사용하면 과도한 각질 제거로 자극이 커질 수 있어요."),
                new Seed("알코올", "AHA", InteractionType.CONFLICT,
                        "알코올의 건조 작용과 AHA의 각질 제거가 겹쳐 자극이 커질 수 있어요."),
                new Seed("나이아신아마이드", "비타민C", InteractionType.SYNERGY,
                        "미백과 항산화 효과를 상호 보완해요."),
                new Seed("판테놀", "히알루론산", InteractionType.SYNERGY,
                        "진정과 보습 효과가 상호 보완돼요."),
                new Seed("판테놀", "세라마이드", InteractionType.SYNERGY,
                        "피부 장벽 강화 효과가 상호 보완돼요."),
                new Seed("글리세린", "히알루론산", InteractionType.SUBSTITUTE,
                        "둘 다 대표적인 보습 성분으로 서로 대체할 수 있어요."),
                new Seed("정제수", "향료", InteractionType.NEUTRAL,
                        "특별한 상호작용이 보고되지 않았어요.")
        );

        for (Seed seed : seeds) {
            Ingredient x = ingredients.get(seed.nameA());
            Ingredient y = ingredients.get(seed.nameB());
            Ingredient a = x.getId() < y.getId() ? x : y;
            Ingredient b = x.getId() < y.getId() ? y : x;
            ingredientInteractionRepository.save(IngredientInteraction.builder()
                    .ingredientAId(a.getId())
                    .ingredientBId(b.getId())
                    .interactionType(seed.type())
                    .description(seed.description())
                    .build());
        }
        return seeds.size();
    }
}