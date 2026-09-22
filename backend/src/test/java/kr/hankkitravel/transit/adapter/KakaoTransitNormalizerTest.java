package kr.hankkitravel.transit.adapter;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import kr.hankkitravel.Fixture;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import kr.hankkitravel.transit.model.TransitResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KakaoTransitNormalizerTest {
    private final KakaoTransitNormalizer parser = new KakaoTransitNormalizer();

    @Test void jejuPreservesWalkingGapAndProviderTotalTime() throws Exception {
        var result = parser.normalize(Fixture.read("kakao_publictraffic_jeju.json"));
        assertThat(result.status()).isEqualTo(TransitResult.Status.OK);
        assertThat(result.routes()).hasSize(9);
        var route = result.routes().get(5);
        assertThat(route.totalTimeMinutes()).isEqualByComparingTo("32.85");
        assertThat(route.totalDistanceMeters()).isEqualTo(6364);
        assertThat(route.fareWon()).isEqualTo(1150);
        assertThat(route.explicitWalkingDistanceMeters()).isEqualTo(102);
        assertThat(route.unaccountedDistanceMeters()).isEqualTo(864);
        assertThat(route.segments()).anyMatch(s -> s.type().equals("WALKING"));
        assertThat(result.routes().get(2).explicitWalkingDistanceMeters()).isEqualTo(203);
        assertThat(result.routes()).anyMatch(r -> r.transferCount() == 1).anyMatch(r -> r.transferCount() == 0);
        assertThat(result.routes().getFirst().segments().getFirst().vehicles()).isNotEmpty();
        assertThat(result.routes().getFirst().segments().getFirst().stops()).isNotEmpty();
        assertThat(route.kakaoMapLandingUrl()).startsWith("https://map.kakao.com/");
        long stepTime = route.segments().stream().mapToLong(s -> s.timeSeconds()).sum();
        assertThat(route.totalTimeMinutes()).isNotEqualByComparingTo(BigDecimal.valueOf(stepTime / 60.0));
    }

    @Test void gyeongjuZeroExplicitWalkingDoesNotEraseUncertainty() throws Exception {
        var result = parser.normalize(Fixture.read("kakao_publictraffic_gyeongju.json"));
        assertThat(result.routes()).hasSize(10).allSatisfy(route -> {
            assertThat(route.fareWon()).isEqualTo(1450);
            assertThat(route.transferCount()).isZero();
            assertThat(route.explicitWalkingDistanceMeters()).isZero();
            assertThat(route.unaccountedDistanceMeters()).isPositive();
        });
        assertThat(result.routes().get(1).totalTimeMinutes()).isEqualByComparingTo("29.383333");
        assertThat(result.routes().get(1).unaccountedDistanceMeters()).isEqualTo(939);
    }

    @Test void clampNegativeGapAndPreserveUnknownTypes() throws Exception {
        var json = Fixture.read("kakao_publictraffic_jeju.json")
                .replace("\"totalDistance\": 9346", "\"totalDistance\": 1")
                .replace("\"type\": \"WALKING\"", "\"type\": \"UNKNOWN\"");
        var result = parser.normalize(json);
        assertThat(result.routes().getFirst().unaccountedDistanceMeters()).isZero();
        assertThat(result.routes().get(5).explicitWalkingDistanceMeters()).isZero();
        assertThat(result.routes().get(5).segments()).anyMatch(s -> s.type().equals("UNKNOWN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"status\":\"NO_RESULTS\"}", "{\"status\":\"OK\",\"routes\":[]}"})
    void noRoutesNeverCreatesFakeRoute(String json) {
        var result = parser.normalize(json);
        assertThat(result.status()).isEqualTo(TransitResult.Status.NO_RESULTS);
        assertThat(result.routes()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"broken", "null", "{}", "{\"status\":\"OK\"}",
            "{\"status\":\"OK\",\"routes\":[null]}", "{\"status\":\"OK\",\"routes\":[{}]}"})
    void malformedResponseIsParsingFailure(String json) {
        assertThatThrownBy(() -> parser.normalize(json)).isInstanceOfSatisfying(IntegrationException.class,
                e -> assertThat(e.failure()).isEqualTo(IntegrationFailure.JSON_PARSING_FAILURE));
    }

    @Test void providerErrorIsDistinct() {
        assertThatThrownBy(() -> parser.normalize("{\"status\":\"ERROR\"}"))
                .isInstanceOfSatisfying(IntegrationException.class,
                        e -> assertThat(e.failure()).isEqualTo(IntegrationFailure.UPSTREAM_REJECTED));
    }

    @Test void untrustedLandingUrlIsNotPropagated() {
        assertThat(parser.normalize("{\"status\":\"NO_RESULTS\",\"properties\":{\"landingURL\":\"https://evil.test\"}}")
                .kakaoMapLandingUrl()).isNull();
    }
}
