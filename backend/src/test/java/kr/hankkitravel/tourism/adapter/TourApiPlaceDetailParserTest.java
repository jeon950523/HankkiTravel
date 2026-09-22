package kr.hankkitravel.tourism.adapter;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TourApiPlaceDetailParserTest {
    @Test void parsesCurrentIdentityPresentationAndCoordinates() {
        String json="""
            {"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":[{
              "contentid":"12001","contenttypeid":"12","title":"현재 관광지","addr1":"현재 주소",
              "firstimage":"https://example.test/live.jpg","mapx":"126.531","mapy":"33.499",
              "lDongRegnCd":"50","lDongSignguCd":"110"}]}}}}
            """;
        var result=new TourApiPlaceDetailParser().parse(json);
        assertThat(result.contentId()).isEqualTo("12001");assertThat(result.contentType()).isEqualTo("12");
        assertThat(result.title()).isEqualTo("현재 관광지");assertThat(result.coordinates().longitude()).isEqualByComparingTo("126.531");
        assertThat(result.regionCode()).isEqualTo("50");
    }
}
