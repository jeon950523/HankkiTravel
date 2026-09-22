package kr.hankkitravel.nutrition.model;

import java.util.List;

public record NutritionSourceData(String datasetName, String sheetName, int columnCount,
        List<NutritionSourceRow> rows) {
    public NutritionSourceData {
        rows = List.copyOf(rows);
    }
}
