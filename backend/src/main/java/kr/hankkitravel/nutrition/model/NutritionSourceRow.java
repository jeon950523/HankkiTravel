package kr.hankkitravel.nutrition.model;

/** Read-only row representation; parsing and filtering happen before persistence. */
public record NutritionSourceRow(int rowNumber, String sourceFoodId, String sourceFoodName, String category,
        String referenceAmountRaw, String energyKcalRaw, String carbohydrateGRaw, String sugarGRaw,
        String proteinGRaw, String fatGRaw, String sodiumMgRaw, String sourceInstitution,
        String sourceGeneratedDateRaw, String sourceDatasetVersionRaw) {
}
