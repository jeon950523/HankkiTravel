package kr.hankkitravel.tourism.adapter;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TourApiAnchorMetadataTest {
    final TourApiRestaurantPresentationParser parser=new TourApiRestaurantPresentationParser();
    @Test void readsCurrentIdentityAndLegalRegionInJsonAndXml() {
        var json=parser.parse("""
            {"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":[{
            "contentid":"91001","contenttypeid":"39","lDongRegnCd":"47","lDongSignguCd":"130"}]}}}}
            """);
        var xml=parser.parse("""
            <response><header><resultCode>0000</resultCode></header><body><items><item>
            <contentid>91001</contentid><contenttypeid>39</contenttypeid><lDongRegnCd>47</lDongRegnCd>
            <lDongSignguCd>130</lDongSignguCd></item></items></body></response>
            """);
        assertThat(json).isEqualTo(xml);
        assertThat(json.contentId()).isEqualTo("91001"); assertThat(json.contentType()).isEqualTo("39");
        assertThat(json.regionCode()).isEqualTo("47"); assertThat(json.districtCode()).isEqualTo("130");
    }
}
