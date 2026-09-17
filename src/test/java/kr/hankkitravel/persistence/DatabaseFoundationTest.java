package kr.hankkitravel.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import kr.hankkitravel.nutrition.application.NutritionImportService;
import kr.hankkitravel.nutrition.persistence.NutritionFoodMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PlanBDatabaseFoundationTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(
            "mysql@sha256:c831a0f11348d402b43d77453e17d770be2eef356615a2823fe0f5a0d6c8b9af")
            .withDatabaseName("hankki_planb_test").withUsername("foundation_test")
            .withPassword(UUID.randomUUID().toString())
            .withCommand("--default-time-zone=+00:00", "--character-set-server=utf8mb4");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws Exception {
        String url = MYSQL.getJdbcUrl() + (MYSQL.getJdbcUrl().contains("?") ? "&" : "?")
                + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        try (var connection = DriverManager.getConnection(url, MYSQL.getUsername(), MYSQL.getPassword())) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        }
    }

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired NutritionImportService nutritionImport;
    @Autowired NutritionFoodMapper nutritionFoods;

    @Test void freshPlanBDatabaseUsesV1ThroughV14AndHasNoTourismPayloadTables() {
        assertThat(flyway.info().applied()).extracting(info -> info.getVersion().toString())
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14");
        assertThat(flyway.info().pending()).isEmpty();
        var restarted = Flyway.configure().dataSource(flyway.getConfiguration().getDataSource())
                .locations("classpath:db/migration").cleanDisabled(true).load();
        assertThat(restarted.migrate().migrationsExecuted).isZero();
        assertThat(restarted.info().pending()).isEmpty();
        assertThat(restarted.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('restaurant_menus', 'restaurant_detail_refresh_runs',
                                     'menu_nutrition_matches', 'restaurant_nutrition_read_models')
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM nutrition_foods", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM nutrition_import_runs", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.check_constraints
                WHERE constraint_schema = DATABASE()
                  AND check_clause LIKE '%POST_MEAL_DESSERT%'
                """, Integer.class)).isPositive();

        Path source = Path.of("src", "test", "resources", "nutrition", "20260828_음식DB_19617건.xlsx").toAbsolutePath().normalize();
        var firstImport = nutritionImport.importFrom(source);
        assertThat(firstImport.sourceRowCount()).isEqualTo(19_617);
        assertThat(firstImport.importedCount()).isEqualTo(19_534);
        assertThat(firstImport.excludedCount()).isEqualTo(83);
        assertThat(firstImport.datasetVersion()).isEqualTo("2026-08-28");
        assertThat(nutritionFoods.countActiveByDatasetVersion("식품영양성분 DB 음식", "2026-08-28")).isEqualTo(19_534);
        var secondImport = nutritionImport.importFrom(source);
        assertThat(secondImport.importedCount()).isEqualTo(19_534);
        assertThat(nutritionFoods.countActiveByDatasetVersion("식품영양성분 DB 음식", "2026-08-28")).isEqualTo(19_534);
    }
}
