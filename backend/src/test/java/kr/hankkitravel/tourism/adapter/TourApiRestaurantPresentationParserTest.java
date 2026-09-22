package kr.hankkitravel.tourism.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import org.junit.jupiter.api.Test;

class TourApiRestaurantPresentationParserTest {
    private final TourApiRestaurantPresentationParser parser = new TourApiRestaurantPresentationParser();

    @Test void parsesCurrentPresentationFromSingleItem() {
        var presentation = parser.parse("""
                {"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":{
                "title":"실시간 식당","addr1":"제주시 테스트로","firstimage":"https://image.example/current.jpg"}}}}}
                """);

        assertThat(presentation.title()).isEqualTo("실시간 식당");
        assertThat(presentation.address()).isEqualTo("제주시 테스트로");
        assertThat(presentation.firstImage()).contains("current.jpg");
    }

    @Test void acceptsMissingOptionalPresentationFields() {
        var presentation = parser.parse("{\"response\":{\"header\":{\"resultCode\":\"0000\"},\"body\":{\"items\":\"\"}}}");
        assertThat(presentation.title()).isNull();
        assertThat(presentation.address()).isNull();
    }
    @Test void keepsMalformedAndQuotaFailuresSafe() {
        assertThatThrownBy(() -> parser.parse("not-json")).isInstanceOfSatisfying(IntegrationException.class,
                exception -> assertThat(exception.failure()).isEqualTo(IntegrationFailure.JSON_PARSING_FAILURE));
        assertThatThrownBy(() -> parser.parse("{\"response\":{\"header\":{\"resultCode\":\"22\"}}}"))
                .isInstanceOfSatisfying(IntegrationException.class,
                        exception -> assertThat(exception.failure()).isEqualTo(IntegrationFailure.QUOTA_EXCEEDED));
    }
}
