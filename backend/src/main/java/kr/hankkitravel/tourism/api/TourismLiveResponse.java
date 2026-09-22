package kr.hankkitravel.tourism.api;

import java.math.BigDecimal;
import java.util.List;

public final class TourismLiveResponse {
    private TourismLiveResponse() { }

    public record RestaurantPage(String region, List<Restaurant> items, int page, int size, int total,
            String sourceAttribution) { }
    public record Restaurant(String contentId, String title, String address, String telephone, String imageUrl) { }

    public record DecisionData(String contentId, String title, String address, String imageUrl, String firstMenu,
            String treatMenu, String openTime, String restDate, String parking, Classification classification,
            List<MenuEvidence> menus, String referenceNotice, String sourceAttribution) { }
    public record Classification(String value, String reason) { }
    public record MenuEvidence(String rawMenuName, String normalizedMenuName, String sourceField, String matchLevel,
            String matchMethod, String reviewState, String evidence, String ruleVersion, String matchedStandardFood,
            ReferenceNutrition referenceNutrition) { }
    public record ReferenceNutrition(String referenceBasis, BigDecimal referenceAmount, String referenceUnit,
            BigDecimal energyKcal, BigDecimal carbohydrateG, BigDecimal sugarG, BigDecimal proteinG,
            BigDecimal fatG, BigDecimal sodiumMg, Provenance provenance) { }
    public record Provenance(String sourceDatasetName, String sourceDatasetVersion, String sourceInstitution) { }
    public record Error(String code, String message) { }
}
