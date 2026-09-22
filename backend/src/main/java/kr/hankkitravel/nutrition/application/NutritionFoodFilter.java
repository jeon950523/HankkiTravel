package kr.hankkitravel.nutrition.application;

import java.util.Set;
import kr.hankkitravel.nutrition.model.NutritionSourceRow;
import org.springframework.stereotype.Component;

/** Conservative exclusions keep raw ingredients and seasonings out of restaurant-menu references. */
@Component
public class NutritionFoodFilter {
    private static final Set<String> UNSUITABLE_CATEGORIES = Set.of(
            "수·조·어·육류", "곡류, 서류 제품", "채소, 해조류", "과일류", "두류, 견과 및 종실류",
            "장류, 양념류", "장아찌·절임류", "젓갈류");

    public FilterDecision decide(NutritionSourceRow row) {
        if (blank(row.sourceFoodId()) || blank(row.sourceFoodName()) || blank(row.category())
                || blank(row.referenceAmountRaw()) || blank(row.energyKcalRaw()) || blank(row.proteinGRaw())
                || blank(row.sourceInstitution()) || blank(row.sourceDatasetVersionRaw())) {
            return FilterDecision.excluded("MISSING_REQUIRED_VALUE");
        }
        if (UNSUITABLE_CATEGORIES.contains(row.category().trim())) return FilterDecision.excluded("RAW_OR_CONDIMENT_CATEGORY");
        if (row.sourceFoodName().matches(".*\\bSKU[-_ ]?\\d+\\b.*")) return FilterDecision.excluded("BRAND_SKU_PATTERN");
        return FilterDecision.allow();
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    public record FilterDecision(boolean included, String exclusionReason) {
        static FilterDecision allow() { return new FilterDecision(true, null); }
        static FilterDecision excluded(String reason) { return new FilterDecision(false, reason); }
    }
}
