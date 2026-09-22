package kr.hankkitravel.recommendation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import kr.hankkitravel.recommendation.application.RecommendationCore;
import kr.hankkitravel.shared.geo.Coordinates;
import org.junit.jupiter.api.Test;

class RestaurantRecommendationControllerTest {
    @Test
    void exposesStraightLineDistanceAndRouteAvailabilityWithoutInventingTravelTime() {
        var restaurant = new RecommendationCore.Candidate("1", "식당", "제주", "제주시", null, null,
                List.of(), null, new Coordinates(new BigDecimal("126.5"), new BigDecimal("33.5")),
                new BigDecimal("1.234"), null, null, "UNAVAILABLE", null);
        var scored = new RecommendationCore.ScoredCandidate(restaurant, Map.of(),
                RecommendationCore.InformationEvidence.REFERENCE, List.of(), List.of(), List.of());
        var ranked = new RecommendationCore.RankedCandidate(scored, RecommendationCore.Perspective.BALANCED,
                80, 70, 70);

        var response = RestaurantRecommendationController.RestaurantResponse.from(ranked);

        assertThat(response.distanceMeters()).isEqualTo(1234L);
        assertThat(response.transportEvidence()).isNull();
        assertThat(response.routeDataAvailability()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void marksNormalizedTransitEvidenceAsCurrentData() {
        var transit = new RecommendationCore.TransitEvidence(new BigDecimal("18.4"), 1, 180, 9999, null);
        var restaurant = new RecommendationCore.Candidate("1", "식당", "제주", "제주시", null, null,
                List.of(), transit, new Coordinates(new BigDecimal("126.5"), new BigDecimal("33.5")),
                new BigDecimal("1.234"), null, null, "UNAVAILABLE", null);
        var scored = new RecommendationCore.ScoredCandidate(restaurant, Map.of(),
                RecommendationCore.InformationEvidence.REFERENCE, List.of(), List.of(), List.of());

        var response = RestaurantRecommendationController.RestaurantResponse.from(
                new RecommendationCore.RankedCandidate(scored, RecommendationCore.Perspective.BALANCED, 80, 70, 70));

        assertThat(response.routeDataAvailability()).isEqualTo("CURRENT_DATA");
        assertThat(response.transportEvidence().explicitWalkingDistanceMeters()).isEqualTo(180);
    }

    @Test
    void exposesLiveMenuSummaryEvenWhenNutritionReferenceIsUnavailable() {
        var menu = new RecommendationCore.MenuEvidence("갈치조림", "NONE", null, null, null, null, "NOT_MATCHED");
        var restaurant = new RecommendationCore.Candidate("1", "식당", "제주", "제주시", null, null,
                List.of(menu), null, new Coordinates(new BigDecimal("126.5"), new BigDecimal("33.5")),
                new BigDecimal("1.234"), null, null, "UNAVAILABLE", null);
        var scored = new RecommendationCore.ScoredCandidate(restaurant, Map.of(),
                RecommendationCore.InformationEvidence.REFERENCE, List.of(), List.of(), List.of());

        var response = RestaurantRecommendationController.RestaurantResponse.from(
                new RecommendationCore.RankedCandidate(scored, RecommendationCore.Perspective.BALANCED, 74, 30, 30));

        assertThat(response.menuSummary()).containsExactly("갈치조림");
        assertThat(response.nutritionEvidence()).isEmpty();
    }
}
