package kr.hankkitravel.recommendation.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OfficialDemandResponseParserTest {
    @Test void normalizesTargetAgainstSameMonthDistrictMinimumAndMaximumWithoutFixedScaleAssumption() {
        String json="""
                {"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":[
                {"signguCd":"0","tarSjrnDsIxVal":"74.49"},
                {"signguCd":"50110","tarSjrnDsIxVal":"125.20"},
                {"signguCd":"50130","tarSjrnDsIxVal":"112.95"}
                ]}}}}
                """;
        assertThat(new OfficialDemandResponseParser().normalizedIndex("TEST",json,"tarSjrnDsIxVal","50110"))
                .isEqualByComparingTo("100.0000");
        assertThat(new OfficialDemandResponseParser().normalizedIndex("TEST",json,"tarSjrnDsIxVal","50130"))
                .isEqualByComparingTo("0.0000");
    }
}
