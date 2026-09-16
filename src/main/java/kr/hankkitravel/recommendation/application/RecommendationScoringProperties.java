package kr.hankkitravel.recommendation.application;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RecommendationScoringProperties {
    private final Map<String,Integer> weights;
    public RecommendationScoringProperties(
            @Value("${hankki.recommendation.weights.family-meal-fit:40}") int familyMeal,
            @Value("${hankki.recommendation.weights.trip-route-fit:20}") int tripRoute,
            @Value("${hankki.recommendation.weights.mobility-fit:10}") int mobility,
            @Value("${hankki.recommendation.weights.area-demand-signal:10}") int areaDemand,
            @Value("${hankki.recommendation.weights.review-signal:10}") int review,
            @Value("${hankki.recommendation.weights.local-menu-fit:10}") int localMenu) {
        var configured=new LinkedHashMap<String,Integer>();
        configured.put(RecommendationCore.FAMILY_MEAL_FIT,familyMeal);configured.put(RecommendationCore.TRIP_ROUTE_FIT,tripRoute);
        configured.put(RecommendationCore.MOBILITY_FIT,mobility);configured.put(RecommendationCore.AREA_DEMAND_SIGNAL,areaDemand);
        configured.put(RecommendationCore.REVIEW_SIGNAL,review);configured.put(RecommendationCore.LOCAL_MENU_FIT,localMenu);
        if(configured.values().stream().anyMatch(value->value<0)||configured.values().stream().mapToInt(Integer::intValue).sum()!=100)
            throw new IllegalArgumentException("추천 가중치 합계는 100이어야 합니다.");
        this.weights=Map.copyOf(configured);
    }
    public RecommendationScoringProperties(){this(40,20,10,10,10,10);}
    public int weight(String code){return weights.getOrDefault(code,0);} public Map<String,Integer> weights(){return weights;}
}
