package domain.routine;

import domain.inventory.Inventory;
import domain.inventory.InventoryRepository;
import domain.inventory.Product;
import domain.inventory.ProductRepository;
import domain.member.Member;
import domain.member.MemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 6.16(AI 자동생성 루틴 미리보기) 로컬 검증용. IngredientDataSeeder가 만든 기존 데모 제품
 * 6개를 그대로 데모 회원의 인벤토리에 담아, generateRoutine()이 카테고리별 후보를 가질 수
 * 있게 한다(새 제품을 만들지 않음 - 이미 성분 매핑이 된 제품이라 4.4 매칭점수가 의미 있게 나옴).
 * IngredientDataSeeder(1)/DemoMemberSeeder(2) 이후에 실행되어야 한다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
@Order(3)
public class DemoInventorySeeder implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (inventoryRepository.count() > 0) {
            log.info("인벤토리 데모 데이터가 이미 존재하여 시딩을 건너뜁니다.");
            return;
        }

        Member member = memberRepository.findAll().stream().findFirst().orElse(null);
        if (member == null) {
            return;
        }

        List<Product> products = productRepository.findAll();
        for (Product product : products) {
            inventoryRepository.save(Inventory.builder().member(member).product(product).build());
        }
        log.info("인벤토리 데모 데이터 시딩 완료: memberId={}, 제품 {}개", member.getId(), products.size());
    }
}
