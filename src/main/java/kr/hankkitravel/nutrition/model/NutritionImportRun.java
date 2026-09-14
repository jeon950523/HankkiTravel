package kr.hankkitravel.nutrition.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NutritionImportRun {
    private Long id;
    private String sourceDatasetName;
    private String sourceDatasetVersion;
    private int sourceRowCount;
    private int importedCount;
    private int excludedCount;
    private String status;
    private String failureSummary;
    private Instant startedAt;
    private Instant completedAt;

    public NutritionImportRun(String sourceDatasetName, String sourceDatasetVersion, int sourceRowCount,
            Instant startedAt) {
        if (blank(sourceDatasetName) || blank(sourceDatasetVersion) || sourceRowCount < 0 || startedAt == null) {
            throw new IllegalArgumentException("영양 적재 실행 정보가 필요합니다.");
        }
        this.sourceDatasetName = sourceDatasetName;
        this.sourceDatasetVersion = sourceDatasetVersion;
        this.sourceRowCount = sourceRowCount;
        this.status = "RUNNING";
        this.startedAt = startedAt;
    }

    public void complete(int importedCount, int excludedCount, String status, String failureSummary,
            Instant completedAt) {
        if (importedCount < 0 || excludedCount < 0 || blank(status) || completedAt == null) {
            throw new IllegalArgumentException("영양 적재 완료 정보가 필요합니다.");
        }
        this.importedCount = importedCount;
        this.excludedCount = excludedCount;
        this.status = status;
        this.failureSummary = failureSummary;
        this.completedAt = completedAt;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
