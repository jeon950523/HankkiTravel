package kr.hankkitravel.nutrition.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NutritionFood {
    private Long id;
    private String sourceFoodId;
    private String sourceFoodName;
    private String normalizedFoodName;
    private String category;
    private String referenceBasis;
    private BigDecimal referenceAmount;
    private String referenceUnit;
    private BigDecimal energyKcal;
    private BigDecimal carbohydrateG;
    private BigDecimal sugarG;
    private BigDecimal proteinG;
    private BigDecimal fatG;
    private BigDecimal sodiumMg;
    private String sourceDatasetName;
    private String sourceDatasetVersion;
    private String sourceInstitution;
    private LocalDate sourceGeneratedDate;
    private boolean active;

    public NutritionFood(String sourceFoodId, String sourceFoodName, String normalizedFoodName, String category,
            String referenceBasis, BigDecimal referenceAmount, String referenceUnit, BigDecimal energyKcal,
            BigDecimal carbohydrateG, BigDecimal sugarG, BigDecimal proteinG, BigDecimal fatG, BigDecimal sodiumMg,
            String sourceDatasetName, String sourceDatasetVersion, String sourceInstitution,
            LocalDate sourceGeneratedDate) {
        if (blank(sourceFoodId) || blank(sourceFoodName) || blank(normalizedFoodName) || blank(category)
                || blank(referenceBasis) || blank(referenceUnit) || blank(sourceDatasetName)
                || blank(sourceDatasetVersion) || blank(sourceInstitution)) {
            throw new IllegalArgumentException("영양 표준 음식의 필수 출처 정보가 필요합니다.");
        }
        this.sourceFoodId = sourceFoodId;
        this.sourceFoodName = sourceFoodName;
        this.normalizedFoodName = normalizedFoodName;
        this.category = category;
        this.referenceBasis = referenceBasis;
        this.referenceAmount = referenceAmount;
        this.referenceUnit = referenceUnit;
        this.energyKcal = energyKcal;
        this.carbohydrateG = carbohydrateG;
        this.sugarG = sugarG;
        this.proteinG = proteinG;
        this.fatG = fatG;
        this.sodiumMg = sodiumMg;
        this.sourceDatasetName = sourceDatasetName;
        this.sourceDatasetVersion = sourceDatasetVersion;
        this.sourceInstitution = sourceInstitution;
        this.sourceGeneratedDate = sourceGeneratedDate;
        this.active = true;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
