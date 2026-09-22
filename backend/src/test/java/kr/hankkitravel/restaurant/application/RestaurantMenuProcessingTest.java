package kr.hankkitravel.restaurant.application;

import static org.assertj.core.api.Assertions.*;

import kr.hankkitravel.restaurant.model.RestaurantClassification;
import org.junit.jupiter.api.Test;

class RestaurantMenuProcessingTest {
    private final RestaurantClassifier classifier = new RestaurantClassifier();
    private final MenuNormalizer normalizer = new MenuNormalizer();

    @Test void classifiesMealCafeMixedAndUnknownWithoutOverstatingEvidence() {
        assertThat(classifier.classify("제주 국밥 한식", null, "흑돼지 구이").classification())
                .isEqualTo(RestaurantClassification.MEAL);
        assertThat(classifier.classify("바다 카페", "아메리카노", "케이크").classification())
                .isEqualTo(RestaurantClassification.CAFE_DESSERT);
        assertThat(classifier.classify("브런치 카페", "국밥 한식", "커피 케이크").classification())
                .isEqualTo(RestaurantClassification.MIXED);
        assertThat(classifier.classify("알 수 없는 장소", null, null).classification())
                .isEqualTo(RestaurantClassification.UNKNOWN);
    }

    @Test void normalizesDelimitedMenusStripsOnlyPricingNoiseAndDeduplicates() {
        var menus = normalizer.normalize("1. 흑돼지 구이(200g) 18,000원, 아메리카노(HOT) 4,000원; 메뉴",
                "흑돼지 구이 / 없음 | 케이크(조각) 6,000원");

        assertThat(menus).extracting(menu -> menu.normalizedMenuName())
                .containsExactly("흑돼지 구이", "아메리카노", "케이크(조각)");
        assertThat(menus.getFirst().sourceField()).isEqualTo("FIRST_MENU");
    }
}
