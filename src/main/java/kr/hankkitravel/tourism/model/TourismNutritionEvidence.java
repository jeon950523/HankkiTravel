package kr.hankkitravel.tourism.model;

import java.math.BigDecimal;

/** Runtime nutrition evidence. LOW/NONE never carries a reference nutrition value. */
public record TourismNutritionEvidence(String rawMenuName, String normalizedMenuName, String sourceField,
        String matchLevel, String matchMethod, String reviewState, String evidence, String ruleVersion,
        String matchedStandardFood, ReferenceNutrition referenceNutrition) {
    public record ReferenceNutrition(String referenceBasis, BigDecimal referenceAmount, String referenceUnit,
            BigDecimal energyKcal, BigDecimal carbohydrateG, BigDecimal sugarG, BigDecimal proteinG,
            BigDecimal fatG, BigDecimal sodiumMg, Provenance provenance) { }
    public record Provenance(String sourceDatasetName, String sourceDatasetVersion, String sourceInstitution) { }
}
