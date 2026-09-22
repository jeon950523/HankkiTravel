package kr.hankkitravel.tourism.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import kr.hankkitravel.tourism.application.TourismNutritionRuntimeMatcher;
import kr.hankkitravel.tourism.application.TourismRealtimeGateway;
import kr.hankkitravel.tourism.application.TourismRealtimeSource;
import kr.hankkitravel.tourism.application.TourismRestaurantRuntimeRules;
import kr.hankkitravel.tourism.model.TourApiPage;
import kr.hankkitravel.tourism.model.TourismMenuCandidate;
import kr.hankkitravel.tourism.model.TourismNutritionEvidence;
import kr.hankkitravel.tourism.model.TourismPlace;
import kr.hankkitravel.tourism.model.TourismRestaurantDetail;
import kr.hankkitravel.tourism.model.TourismRestaurantRuntimeData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TourismLiveControllerTest {
    private TourismRealtimeSource source;
    private TourismRestaurantRuntimeRules restaurantRules;
    private TourismNutritionRuntimeMatcher nutrition;
    private TourismLiveController controller;

    @BeforeEach void setUp() {
        source = mock(TourismRealtimeSource.class);
        restaurantRules = mock(TourismRestaurantRuntimeRules.class);
        nutrition = mock(TourismNutritionRuntimeMatcher.class);
        controller = new TourismLiveController(new TourismRealtimeGateway(source, restaurantRules, nutrition, 30));
    }

    @Test void listUsesLiveSourceAndReturnsNoStoreAttribution() {
        var place = new TourismPlace("123", "39", "현재 식당", "제주시 테스트로", null, null, null, null,
                "https://image.example/current.jpg", null, null, "50", "110", null, null, null, null, null, null);
        when(source.fetchRestaurantPage(any(), org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(20)))
                .thenReturn(new TourApiPage(List.of(place), 1, 20, 1));

        var response = controller.restaurants("jeju-city", 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().items()).extracting(TourismLiveResponse.Restaurant::contentId).containsExactly("123");
        assertThat(response.getBody().sourceAttribution()).isEqualTo("출처: ⓒ한국관광공사");
        verify(source).fetchRestaurantPage(any(), org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(20));
    }

    @Test void decisionComposesTransientEvidenceWithoutHealthClaim() {
        var detail = new TourismRestaurantDetail("비빔밥", null, null, null, null,
                "현재 식당", "제주시 테스트로", null);
        var candidate = new TourismMenuCandidate("비빔밥 10,000원", "비빔밥", "FIRST_MENU");
        var reference = new TourismNutritionEvidence.ReferenceNutrition("100G", new BigDecimal("100"), "g",
                new BigDecimal("150"), null, null, new BigDecimal("5"), null, null,
                new TourismNutritionEvidence.Provenance("식품영양성분 DB 음식", "2026-08-28", "식약처"));
        when(source.fetchRestaurantDetail("123")).thenReturn(detail);
        when(restaurantRules.evaluate(detail)).thenReturn(new TourismRestaurantRuntimeData("MEAL", "MEAL_HINTS", List.of(candidate)));
        when(nutrition.match(candidate)).thenReturn(new TourismNutritionEvidence(candidate.rawMenuName(),
                candidate.normalizedMenuName(), candidate.sourceField(), "HIGH", "EXACT_NORMALIZED", "AUTO_MATCHED",
                "비빔밥", "menu-nutrition-rules-v1", "비빔밥", reference));

        var response = controller.decisionData("123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().menus()).extracting(TourismLiveResponse.MenuEvidence::matchLevel).containsExactly("HIGH");
        assertThat(response.getBody().referenceNotice()).contains("표준 음식 참고정보");
        assertThat(response.getBody().referenceNotice()).doesNotContain("안전", "위험", "치료");
    }
}
