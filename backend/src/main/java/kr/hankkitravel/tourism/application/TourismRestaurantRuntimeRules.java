package kr.hankkitravel.tourism.application;

import kr.hankkitravel.tourism.model.TourismRestaurantDetail;
import kr.hankkitravel.tourism.model.TourismRestaurantRuntimeData;

/** Tourism-owned port for restaurant pure rules. */
public interface TourismRestaurantRuntimeRules {
    TourismRestaurantRuntimeData evaluate(TourismRestaurantDetail detail);
}
