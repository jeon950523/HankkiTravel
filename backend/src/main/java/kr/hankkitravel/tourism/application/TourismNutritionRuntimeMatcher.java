package kr.hankkitravel.tourism.application;

import kr.hankkitravel.tourism.model.TourismMenuCandidate;
import kr.hankkitravel.tourism.model.TourismNutritionEvidence;

/** Tourism-owned port for local nutrition reference matching. */
public interface TourismNutritionRuntimeMatcher {
    TourismNutritionEvidence match(TourismMenuCandidate candidate);
}
