package kr.hankkitravel.tourism.adapter;

import static org.assertj.core.api.Assertions.*;
import kr.hankkitravel.Fixture;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TourApiParserTest {
    private final TourApiParser parser = new TourApiParser();

    @ParameterizedTest
    @ValueSource(strings = {"jeju", "gyeongju"})
    void realFixtureParsesWithoutExposingRawDto(String region) throws Exception {
        var places = parser.parse(Fixture.read("tourapi_" + region + "_attractions_sample.json"));
        assertThat(places).hasSize(2);
        assertThat(places.getFirst().contentId()).isNotBlank();
        assertThat(places.getFirst().coordinates().longitude()).isBetween(
                new java.math.BigDecimal("124"), new java.math.BigDecimal("132"));
        assertThat(places.getFirst().coordinates().latitude()).isBetween(
                new java.math.BigDecimal("33"), new java.math.BigDecimal("39"));
    }

    @Test void acceptsSingleItemAndPartialOptionalData() {
        var places = parser.parse(envelope("{\"item\":{\"contentid\":\"123\",\"mapx\":\"\",\"mapy\":null}}", 1));
        assertThat(places).hasSize(1);
        assertThat(places.getFirst().coordinates()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "{}", "{\"item\":[]}"})
    void emptyItemsWithZeroCount(String items) {
        assertThat(parser.parse(envelope(items, 0))).isEmpty();
    }

    @Test void missingItemsAreNotSilentlySuccessfulWhenTotalIsPositive() {
        assertFailure(envelope("\"\"", 5), IntegrationFailure.JSON_PARSING_FAILURE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"broken", "null", "{}", "{\"response\":{\"header\":{\"resultCode\":\"0000\"}}}"})
    void malformedResponse(String json) { assertFailure(json, IntegrationFailure.JSON_PARSING_FAILURE); }

    @Test void quotaErrorIsDistinct() {
        assertFailure("{\"response\":{\"header\":{\"resultCode\":\"22\"}}}", IntegrationFailure.QUOTA_EXCEEDED);
    }

    private String envelope(String items, int total) {
        return "{\"response\":{\"header\":{\"resultCode\":\"0000\"},\"body\":{\"items\":"
                + items + ",\"totalCount\":" + total + "}}}";
    }

    private void assertFailure(String json, IntegrationFailure kind) {
        assertThatThrownBy(() -> parser.parse(json)).isInstanceOfSatisfying(IntegrationException.class,
                e -> assertThat(e.failure()).isEqualTo(kind));
    }
}
