package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class FlywayMigrationVersionTest {

    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("^V(.+?)__.+\\.(?:java|sql)$");

    @Test
    void versionedMigrationsHaveUniqueVersions() throws IOException {
        Map<MigrationVersion, List<String>> migrationsByVersion = new LinkedHashMap<>();

        collectMigrations(Path.of("src/main/java/db/migration"), migrationsByVersion);
        collectMigrations(Path.of("src/main/resources/db/migration"), migrationsByVersion);

        Map<MigrationVersion, List<String>> duplicates = new LinkedHashMap<>();
        migrationsByVersion.forEach((version, migrations) -> {
            if (migrations.size() > 1) {
                duplicates.put(version, migrations);
            }
        });

        assertThat(duplicates)
                .as("Flyway migration versions must be unique")
                .isEmpty();
    }

    private void collectMigrations(
            Path directory,
            Map<MigrationVersion, List<String>> migrationsByVersion
    ) throws IOException {
        if (Files.notExists(directory)) {
            return;
        }

        try (var paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                Matcher matcher = VERSIONED_MIGRATION.matcher(path.getFileName().toString());
                if (!matcher.matches()) {
                    return;
                }

                MigrationVersion version = MigrationVersion.fromVersion(
                        matcher.group(1).replace('_', '.')
                );
                migrationsByVersion
                        .computeIfAbsent(version, ignored -> new ArrayList<>())
                        .add(path.toString());
            });
        }
    }
}
