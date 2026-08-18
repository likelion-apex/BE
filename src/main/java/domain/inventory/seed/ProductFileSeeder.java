package domain.inventory.seed;

import domain.inventory.CategoryImageResolver;
import domain.inventory.Product;
import domain.inventory.ProductCategory;
import domain.inventory.ProductNameNormalizer;
import domain.inventory.ProductRepository;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * classpath CSV를 읽어 상품 마스터를 정규화 키 기준으로 upsert한다.
 * 기동 헬스체크를 막지 않도록 ApplicationReadyEvent 이후 가상 스레드에서 실행한다.
 */
@Slf4j
@Component
public class ProductFileSeeder {

    private static final int BATCH_SIZE = 100;
    private static final String EXPECTED_HEADER = "name,brand,category,imageUrl";

    private final ProductRepository productRepository;
    private final CategoryImageResolver categoryImageResolver;
    private final TransactionTemplate transactionTemplate;
    private final boolean enabled;
    private final Resource seedResource;

    public ProductFileSeeder(
            ProductRepository productRepository,
            CategoryImageResolver categoryImageResolver,
            PlatformTransactionManager transactionManager,
            @Value("${product.seed.enabled:true}") boolean enabled,
            @Value("${product.seed.location:classpath:data/products-seed.csv}") Resource seedResource) {
        this.productRepository = productRepository;
        this.categoryImageResolver = categoryImageResolver;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
        this.seedResource = seedResource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        if (!enabled) {
            log.info("상품 CSV 시딩이 비활성화되어 건너뜁니다.");
            return;
        }
        log.info("상품 CSV 시딩을 백그라운드에서 시작합니다.");
        Thread.ofVirtual().name("product-file-seeder").start(this::seed);
    }

    void seed() {
        List<SeedRow> rows;
        try {
            rows = parseCsv();
        } catch (Exception e) {
            log.error("상품 CSV 시딩 파일을 읽지 못해 건너뜁니다.", e);
            return;
        }
        if (rows.isEmpty()) {
            log.info("상품 CSV에 적재할 행이 없습니다.");
            return;
        }

        int inserted = 0;
        int updated = 0;
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<SeedRow> batch = rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            int[] counts = transactionTemplate.execute(status -> {
                int batchInserted = 0;
                int batchUpdated = 0;
                for (SeedRow row : batch) {
                    if (upsert(row)) {
                        batchInserted++;
                    } else {
                        batchUpdated++;
                    }
                }
                return new int[] {batchInserted, batchUpdated};
            });
            if (counts != null) {
                inserted += counts[0];
                updated += counts[1];
            }
        }
        log.info("상품 CSV 시딩 완료: insert={}, update={}, total={}", inserted, updated, rows.size());
    }

    private boolean upsert(SeedRow row) {
        Optional<Product> existing = productRepository.findByNormalizedName(
                ProductNameNormalizer.canonicalKey(row.name()));
        String imageUrl = resolveImageUrl(row, existing.orElse(null));
        if (existing.isPresent()) {
            existing.get().update(row.brand(), row.category(), imageUrl);
            return false;
        }
        productRepository.save(Product.builder()
                .name(row.name())
                .brand(row.brand())
                .category(row.category())
                .imageUrl(imageUrl)
                .build());
        return true;
    }

    private String resolveImageUrl(SeedRow row, Product existing) {
        if (StringUtils.hasText(row.imageUrl())) {
            return row.imageUrl();
        }
        if (existing != null && StringUtils.hasText(existing.getImageUrl())) {
            return existing.getImageUrl();
        }
        return categoryImageResolver.resolve(row.category());
    }

    private List<SeedRow> parseCsv() throws Exception {
        if (!seedResource.exists()) {
            throw new IllegalStateException("상품 시드 파일이 없습니다: " + seedResource);
        }
        List<SeedRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(seedResource.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header != null && header.startsWith("\uFEFF")) {
                header = header.substring(1);
            }
            if (header == null || !EXPECTED_HEADER.equalsIgnoreCase(header.trim())) {
                throw new IllegalStateException("상품 시드 CSV 헤더가 올바르지 않습니다. 기대값: " + EXPECTED_HEADER);
            }
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                SeedRow row = parseRow(line, lineNumber);
                if (row != null) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private SeedRow parseRow(String line, int lineNumber) {
        List<String> columns = splitCsv(line);
        if (columns.size() < 3) {
            log.warn("상품 CSV {}행을 건너뜁니다. 컬럼이 부족합니다: {}", lineNumber, line);
            return null;
        }
        String name = columns.get(0).trim();
        if (!StringUtils.hasText(name)) {
            log.warn("상품 CSV {}행을 건너뜁니다. name이 비어 있습니다.", lineNumber);
            return null;
        }
        String brand = blankToNull(columns.get(1));
        ProductCategory category;
        try {
            category = ProductCategory.valueOf(columns.get(2).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("상품 CSV {}행을 건너뜁니다. 알 수 없는 category입니다: {}", lineNumber, columns.get(2));
            return null;
        }
        String imageUrl = columns.size() > 3 ? blankToNull(columns.get(3)) : null;
        return new SeedRow(name, brand, category, imageUrl);
    }

    static List<String> splitCsv(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        columns.add(current.toString());
        return columns;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    record SeedRow(String name, String brand, ProductCategory category, String imageUrl) {
    }
}
