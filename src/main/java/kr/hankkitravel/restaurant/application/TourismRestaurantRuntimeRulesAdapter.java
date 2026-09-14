package kr.hankkitravel.restaurant.application;

import kr.hankkitravel.tourism.application.TourismRestaurantRuntimeRules;
import kr.hankkitravel.tourism.model.TourismMenuCandidate;
import kr.hankkitravel.tourism.model.TourismRestaurantDetail;
import kr.hankkitravel.tourism.model.TourismRestaurantRuntimeData;
import org.springframework.stereotype.Component;

/** Adapts restaurant pure rules to Tourism's live runtime port. */
@Component
public class TourismRestaurantRuntimeRulesAdapter implements TourismRestaurantRuntimeRules {
    private final RestaurantClassifier classifier;
    private final MenuNormalizer normalizer;

    public TourismRestaurantRuntimeRulesAdapter(RestaurantClassifier classifier, MenuNormalizer normalizer) {
        this.classifier = classifier; this.normalizer = normalizer;
    }

    @Override
    public TourismRestaurantRuntimeData evaluate(TourismRestaurantDetail detail) {
        var classification = classifier.classify(detail.title(), detail.firstMenu(), detail.treatMenu());
        var menus = normalizer.normalize(detail.firstMenu(), detail.treatMenu()).stream()
                .map(menu -> new TourismMenuCandidate(menu.rawMenuName(), menu.normalizedMenuName(), menu.sourceField()))
                .toList();
        return new TourismRestaurantRuntimeData(classification.classification().name(), classification.reason(), menus);
    }
}
