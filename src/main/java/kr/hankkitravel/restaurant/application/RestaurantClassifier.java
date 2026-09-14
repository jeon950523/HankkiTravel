package kr.hankkitravel.restaurant.application;

import java.util.List;
import java.util.Locale;
import kr.hankkitravel.restaurant.model.RestaurantClassification;
import kr.hankkitravel.restaurant.model.RestaurantClassificationResult;
import org.springframework.stereotype.Component;

@Component
public class RestaurantClassifier {
    public static final String VERSION = "restaurant-classifier-v1";
    private static final List<String> MEAL_HINTS = List.of("국밥", "찌개", "전골", "비빔밥", "백반", "정식", "한식", "갈비", "흑돼지", "삼겹", "회", "해장", "국수", "냉면", "밀면", "돈까스", "분식", "식당", "음식점", "밥집", "구이", "탕");
    private static final List<String> CAFE_HINTS = List.of("카페", "커피", "coffee", "베이커리", "디저트", "케이크", "빵", "브런치", "라떼", "에이드", "주스", "차", "tea", "젤라또", "아이스크림");

    public RestaurantClassificationResult classify(String title, String firstMenu, String treatMenu) {
        var text = String.join(" ", safe(title), safe(firstMenu), safe(treatMenu)).toLowerCase(Locale.ROOT);
        if (text.isBlank()) return new RestaurantClassificationResult(RestaurantClassification.UNKNOWN, "NO_CLASSIFIABLE_TEXT");
        int meal = score(text, MEAL_HINTS);
        int cafe = score(text, CAFE_HINTS);
        if (meal >= 2 && cafe >= 2) return new RestaurantClassificationResult(RestaurantClassification.MIXED, "MEAL_AND_CAFE_HINTS");
        if (meal > cafe) return new RestaurantClassificationResult(RestaurantClassification.MEAL, "MEAL_HINTS");
        if (cafe > meal) return new RestaurantClassificationResult(RestaurantClassification.CAFE_DESSERT, "CAFE_DESSERT_HINTS");
        return new RestaurantClassificationResult(RestaurantClassification.UNKNOWN, "INSUFFICIENT_OR_CONFLICTING_HINTS");
    }

    private int score(String text, List<String> hints) {
        return (int) hints.stream().filter(text::contains).count();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
