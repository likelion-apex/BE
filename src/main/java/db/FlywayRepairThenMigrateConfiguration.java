package db;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 실패한 V3 기록과 수정된 Java 마이그레이션 체크섬을 맞춘 뒤 migrate 한다.
 * MySQL은 DDL 트랜잭션이 없어 실패 버전이 history에 남으면 기동이 막힌다.
 */
@Configuration
@Profile("prod")
public class FlywayRepairThenMigrateConfiguration {

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
