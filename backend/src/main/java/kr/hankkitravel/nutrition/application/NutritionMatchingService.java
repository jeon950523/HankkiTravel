package kr.hankkitravel.nutrition.application;

import kr.hankkitravel.nutrition.model.MenuNutritionMatch;
import kr.hankkitravel.nutrition.model.NutritionMatchDecision;
import kr.hankkitravel.nutrition.model.NutritionMatchLevel;
import kr.hankkitravel.nutrition.model.NutritionReviewState;
import kr.hankkitravel.nutrition.persistence.NutritionMatchingMapper;
import org.springframework.stereotype.Service;

/** Builds transient live-response evidence; no TourAPI menu or match result is persisted. */
@Service
public class NutritionMatchingService {
    private final NutritionMatchingMapper matches;
    private final MenuNutritionMatcher matcher;

    public NutritionMatchingService(NutritionMatchingMapper matches, MenuNutritionMatcher matcher) {
        this.matches = matches; this.matcher = matcher;
    }

    public MenuNutritionMatch match(String rawMenuName, String normalizedMenuName, String sourceField) {
        NutritionMatchDecision decision = matcher.match(normalizedMenuName);
        var reference = decision.nutritionFoodId() == null ? null : matches.findActiveReference(decision.nutritionFoodId());
        if (decision.nutritionFoodId() != null && reference == null) {
            decision = new NutritionMatchDecision(null, NutritionMatchLevel.LOW, "REFERENCE_UNAVAILABLE",
                    NutritionReviewState.REVIEW_REQUIRED, decision.evidence());
        }
        return new MenuNutritionMatch(rawMenuName, normalizedMenuName, sourceField, decision,
                MenuNutritionMatcher.RULE_VERSION, reference);
    }
}
