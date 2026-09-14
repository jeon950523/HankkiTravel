package kr.hankkitravel.nutrition.application;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.hankkitravel.nutrition.adapter.NutritionSourceReader;
import kr.hankkitravel.nutrition.model.NutritionFood;
import kr.hankkitravel.nutrition.model.NutritionImportResult;
import kr.hankkitravel.nutrition.model.NutritionImportRun;
import kr.hankkitravel.nutrition.model.NutritionSourceRow;
import kr.hankkitravel.nutrition.persistence.NutritionFoodMapper;
import kr.hankkitravel.nutrition.persistence.NutritionImportRunMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit, idempotent xlsx import. The normal web application never calls this service on startup. */
@Service
public class NutritionImportService {
    static final String DATASET_NAME = "식품영양성분 DB 음식";
    private static final int BATCH_SIZE = 200;
    private final NutritionSourceReader reader;
    private final NutritionFoodFilter filter;
    private final NutritionNameNormalizer names;
    private final NutritionFoodMapper foods;
    private final NutritionImportRunMapper runs;
    private final TransactionTemplate transactions;

    public NutritionImportService(NutritionSourceReader reader, NutritionFoodFilter filter, NutritionNameNormalizer names,
            NutritionFoodMapper foods, NutritionImportRunMapper runs, PlatformTransactionManager transactionManager) {
        this.reader = reader; this.filter = filter; this.names = names; this.foods = foods; this.runs = runs;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public NutritionImportResult importFrom(Path path) {
        long startedNanos = System.nanoTime();
        var source = reader.read(path);
        String version = singleVersion(source.rows());
        var run = new NutritionImportRun(DATASET_NAME, version, source.rows().size(), Instant.now());
        runs.insert(run);
        var exclusions = new LinkedHashMap<String, Integer>();
        var usable = new ArrayList<NutritionFood>();
        for (var row : source.rows()) {
            var decision = filter.decide(row);
            if (!decision.included()) {
                exclusions.merge(decision.exclusionReason(), 1, Integer::sum);
                continue;
            }
            try {
                usable.add(toFood(row, version));
            } catch (IllegalArgumentException exception) {
                exclusions.merge("MALFORMED_NUMERIC_OR_DATE", 1, Integer::sum);
            }
        }
        try {
            transactions.executeWithoutResult(status -> {
                foods.deactivateOtherVersions(DATASET_NAME, version);
                for (int from = 0; from < usable.size(); from += BATCH_SIZE) {
                    foods.upsertBatch(usable.subList(from, Math.min(from + BATCH_SIZE, usable.size())));
                }
            });
            // Live menu matches are evaluated only while composing a user response.
            run.complete(usable.size(), source.rows().size() - usable.size(), "SUCCESS", null, Instant.now());
            runs.complete(run);
            return new NutritionImportResult(source.datasetName(), version, source.rows().size(), usable.size(),
                    source.rows().size() - usable.size(), exclusions, (System.nanoTime() - startedNanos) / 1_000_000);
        } catch (RuntimeException exception) {
            run.complete(0, 0, "FAILED", "PERSISTENCE_OR_UNEXPECTED", Instant.now());
            runs.complete(run);
            throw exception;
        }
    }

    private NutritionFood toFood(NutritionSourceRow row, String version) {
        var basis = basis(row.referenceAmountRaw());
        return new NutritionFood(row.sourceFoodId().trim(), row.sourceFoodName().trim(), names.normalize(row.sourceFoodName()),
                row.category().trim(), basis.code(), basis.amount(), basis.unit(), requiredNumber(row.energyKcalRaw()),
                optionalNumber(row.carbohydrateGRaw()), optionalNumber(row.sugarGRaw()), requiredNumber(row.proteinGRaw()),
                optionalNumber(row.fatGRaw()), optionalNumber(row.sodiumMgRaw()), DATASET_NAME, version,
                row.sourceInstitution().trim(), optionalDate(row.sourceGeneratedDateRaw()));
    }

    private String singleVersion(List<NutritionSourceRow> rows) {
        var versions = rows.stream().map(NutritionSourceRow::sourceDatasetVersionRaw).filter(value -> value != null && !value.isBlank())
                .map(NutritionImportService::parseSourceDate).map(LocalDate::toString).distinct().toList();
        if (versions.size() != 1) throw new IllegalArgumentException("원본 데이터 기준일이 단일 버전이 아닙니다.");
        return versions.getFirst();
    }

    private Basis basis(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (value.matches("100(?:\\.0+)?g")) return new Basis("100G", new BigDecimal("100"), "g");
        if (value.matches("100(?:\\.0+)?ml")) return new Basis("100ML", new BigDecimal("100"), "ml");
        var digits = value.replaceAll("[^0-9.]", "");
        return new Basis("OTHER", digits.isBlank() ? null : new BigDecimal(digits), "other");
    }

    private BigDecimal requiredNumber(String raw) {
        var value = optionalNumber(raw);
        if (value == null) throw new IllegalArgumentException("필수 영양값이 없습니다.");
        return value;
    }

    private BigDecimal optionalNumber(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return new BigDecimal(raw.replace(",", "")); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("숫자 형식이 아닙니다.", exception); }
    }

    private LocalDate optionalDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return parseSourceDate(raw);
    }
    static LocalDate parseSourceDate(String raw) {
        String value = raw.trim();
        try {
            return LocalDate.parse(value.replace('.', '-').replace('/', '-'), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("M/d/uu", Locale.ROOT));
        }
    }

    private record Basis(String code, BigDecimal amount, String unit) { }
}
