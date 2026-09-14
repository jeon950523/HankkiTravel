package kr.hankkitravel.tourism.adapter;

import static org.assertj.core.api.Assertions.*;

import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import org.junit.jupiter.api.Test;

class TourApiRestaurantDetailParserTest {
    private final TourApiRestaurantDetailParser parser = new TourApiRestaurantDetailParser();

    @Test void parsesFoodDetailIntroFieldsFromSingleItem() {
        var detail = parser.parse("""
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":{
                "firstmenu":"흑돼지구이","treatmenu":"국밥, 냉면","opentimefood":"09:00~21:00",
                "restdatefood":"연중무휴","parkingfood":"가능","seat":"42"}},"pageNo":1,"numOfRows":10,"totalCount":1}}}
                """);

        assertThat(detail.firstMenu()).isEqualTo("흑돼지구이");
        assertThat(detail.treatMenu()).isEqualTo("국밥, 냉면");
        assertThat(detail.openTime()).isEqualTo("09:00~21:00");
        assertThat(detail.restDate()).isEqualTo("연중무휴");
        assertThat(detail.parking()).isEqualTo("가능");
    }

    @Test void acceptsAnEmptySuccessfulDetailAsMissingRuntimeEvidence() {
        var detail = parser.parse("{\"response\":{\"header\":{\"resultCode\":\"0000\"},\"body\":{\"items\":\"\"}}}");
        assertThat(detail.firstMenu()).isNull();
        assertThat(detail.treatMenu()).isNull();
    }
    @Test void keepsQuotaFailureDistinct() {
        assertThatThrownBy(() -> parser.parse("{\"response\":{\"header\":{\"resultCode\":\"22\"}}}"))
                .isInstanceOfSatisfying(IntegrationException.class,
                        exception -> assertThat(exception.failure()).isEqualTo(IntegrationFailure.QUOTA_EXCEEDED));
    }
}
