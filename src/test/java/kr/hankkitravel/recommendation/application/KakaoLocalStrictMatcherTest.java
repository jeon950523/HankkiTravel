package kr.hankkitravel.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import kr.hankkitravel.shared.geo.Coordinates;
import org.junit.jupiter.api.Test;

class KakaoLocalStrictMatcherTest {
    private final KakaoLocalStrictMatcher matcher = new KakaoLocalStrictMatcher(120);
    private final Coordinates origin = point("126.5312","33.4996");

    @Test void exactNameAndAddressMatches() {
        assertThat(matcher.match("한끼식당","제주 제주시 중앙로 1",origin,List.of(
                place("한끼식당","제주 제주시 중앙로 1",point("127","37"),"음식점 > 한식"))).status())
                .isEqualTo(ContactEnrichmentProvider.Status.MATCHED);
    }

    @Test void exactNameAndCloseCoordinatesMatches() {
        assertThat(matcher.match("한끼식당","다른 주소",origin,List.of(
                place("한끼식당","불일치",point("126.5313","33.4997"),"음식점 > 한식"))).status())
                .isEqualTo(ContactEnrichmentProvider.Status.MATCHED);
    }

    @Test void differentBranchFarPlaceAndNonRestaurantAreRejected() {
        assertThat(matcher.match("한끼식당 제주점","주소 없음",origin,List.of(
                place("한끼식당 서귀포점","주소 없음",point("126.5313","33.4997"),"음식점 > 한식"))).status())
                .isEqualTo(ContactEnrichmentProvider.Status.REJECTED);
        assertThat(matcher.match("한끼식당","주소 없음",origin,List.of(
                place("한끼식당","주소 없음",point("127","37"),"음식점 > 한식"))).status())
                .isEqualTo(ContactEnrichmentProvider.Status.REJECTED);
        assertThat(matcher.match("한끼식당","제주 제주시 중앙로 1",origin,List.of(
                place("한끼식당","제주 제주시 중앙로 1",origin,"쇼핑 > 식품"))).status())
                .isEqualTo(ContactEnrichmentProvider.Status.REJECTED);
    }

    @Test void multipleConfidentCandidatesAreAmbiguous() {
        var place=place("한끼식당","제주 제주시 중앙로 1",origin,"음식점 > 한식");
        assertThat(matcher.match("한끼식당","제주 제주시 중앙로 1",origin,List.of(place,place)).status())
                .isEqualTo(ContactEnrichmentProvider.Status.AMBIGUOUS);
    }

    private ContactEnrichmentProvider.Place place(String name,String address,Coordinates point,String category) {
        return new ContactEnrichmentProvider.Place(name,"064-000-0000",address,address,point,"https://place.map.kakao.com/1",category);
    }
    private Coordinates point(String x,String y) { return new Coordinates(new BigDecimal(x),new BigDecimal(y)); }
}
