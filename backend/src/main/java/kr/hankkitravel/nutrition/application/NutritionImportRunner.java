package kr.hankkitravel.nutrition.application;

import java.nio.file.Path;
import kr.hankkitravel.nutrition.persistence.NutritionFoodMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Enabled only by nutrition-import. NUTRITION_SOURCE_PATH has no production default. */
@Component
@Profile("nutrition-import")
@Order(Ordered.LOWEST_PRECEDENCE)
public class NutritionImportRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(NutritionImportRunner.class);
    private final NutritionImportService service;
    private final NutritionFoodMapper foods;
    private final ConfigurableApplicationContext context;
    private final String sourcePath;

    public NutritionImportRunner(NutritionImportService service, NutritionFoodMapper foods, ConfigurableApplicationContext context,
            @Value("${NUTRITION_SOURCE_PATH:}") String sourcePath) {
        this.service = service; this.foods = foods; this.context = context; this.sourcePath = sourcePath;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (sourcePath == null || sourcePath.isBlank()) throw new IllegalArgumentException("NUTRITION_SOURCE_PATH가 필요합니다.");
        var result = service.importFrom(Path.of(sourcePath));
        long active = foods.countActiveByDatasetVersion(NutritionImportService.DATASET_NAME, result.datasetVersion());
        log.info("Nutrition import completed: sourceRows={}, imported={}, excluded={}, activeReferenceRows={}, version={}",
                result.sourceRowCount(), result.importedCount(), result.excludedCount(), active, result.datasetVersion());
        SpringApplication.exit(context, () -> 0);
    }
}
