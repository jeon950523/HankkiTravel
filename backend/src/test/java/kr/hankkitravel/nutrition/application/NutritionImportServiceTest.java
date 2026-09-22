package kr.hankkitravel.nutrition.application;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class NutritionImportServiceTest {
    @Test void canonicalizesIsoAndExcelShortDateVersions() {
        assertThat(NutritionImportService.parseSourceDate("2026-08-28")).isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(NutritionImportService.parseSourceDate("8/28/26")).isEqualTo(LocalDate.of(2026, 8, 28));
    }
}
