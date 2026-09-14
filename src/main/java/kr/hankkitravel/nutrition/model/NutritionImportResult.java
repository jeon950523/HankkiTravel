package kr.hankkitravel.nutrition.model;

import java.util.Map;

public record NutritionImportResult(String datasetName, String datasetVersion, int sourceRowCount,
        int importedCount, int excludedCount, Map<String, Integer> exclusionCounts, long elapsedMillis) {
    public NutritionImportResult {
        exclusionCounts = Map.copyOf(exclusionCounts);
    }
}
