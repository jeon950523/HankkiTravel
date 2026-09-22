package kr.hankkitravel.nutrition.application;

import kr.hankkitravel.tourism.application.TourismNutritionRuntimeMatcher;
import kr.hankkitravel.tourism.model.TourismMenuCandidate;
import kr.hankkitravel.tourism.model.TourismNutritionEvidence;
import org.springframework.stereotype.Component;

/** Adapts nutrition reference matching to Tourism's request-scoped evidence port. */
@Component
public class TourismNutritionRuntimeMatcherAdapter implements TourismNutritionRuntimeMatcher {
    private final NutritionMatchingService matching;

    public TourismNutritionRuntimeMatcherAdapter(NutritionMatchingService matching) {
        this.matching = matching;
    }

    @Override
    public TourismNutritionEvidence match(TourismMenuCandidate candidate) {
        var match = matching.match(candidate.rawMenuName(), candidate.normalizedMenuName(), candidate.sourceField());
        var decision = match.decision();
        var food = match.referenceFood();
        TourismNutritionEvidence.ReferenceNutrition reference = food == null ? null
                : new TourismNutritionEvidence.ReferenceNutrition(food.getReferenceBasis(), food.getReferenceAmount(),
                        food.getReferenceUnit(), food.getEnergyKcal(), food.getCarbohydrateG(), food.getSugarG(),
                        food.getProteinG(), food.getFatG(), food.getSodiumMg(),
                        new TourismNutritionEvidence.Provenance(food.getSourceDatasetName(),
                                food.getSourceDatasetVersion(), food.getSourceInstitution()));
        return new TourismNutritionEvidence(match.rawMenuName(), match.normalizedMenuName(), match.sourceField(),
                decision.matchLevel().name(), decision.matchMethod(), decision.reviewState().name(), decision.evidence(),
                match.ruleVersion(), food == null ? null : food.getSourceFoodName(), reference);
    }
}
