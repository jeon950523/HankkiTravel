package kr.hankkitravel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.math.BigDecimal;
import java.time.Duration;
import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.shared.integration.ExternalHttpClient;
import kr.hankkitravel.tourism.adapter.*;
import kr.hankkitravel.transit.adapter.*;
import kr.hankkitravel.transit.model.TransitResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("live")
class LiveIntegrationTest {
    private final ExternalHttpClient http = new ExternalHttpClient(Duration.ofSeconds(3), Duration.ofSeconds(15));
    @Test void tourApiOnePlace() throws Exception {
        String key = LocalEnvironment.get("DATA_GO_KR_SERVICE_KEY");
        assumeTrue(!key.isBlank(), "SKIPPED_SECRET_NOT_PRESENT: DATA_GO_KR_SERVICE_KEY");
        var places = new TourApiClient(http, new TourApiParser(),
                "https://apis.data.go.kr/B551011/KorService2", key).fetchPage(1, 1);
        assertThat(places.size()).isEqualTo(1);
        assertThat(places.getFirst().contentId() != null).isTrue();
    }
    @Test void kakaoJejuOneCoordinatePair() throws Exception {
        String key = LocalEnvironment.get("KAKAO_REST_API_KEY");
        assumeTrue(!key.isBlank(), "SKIPPED_SECRET_NOT_PRESENT: KAKAO_REST_API_KEY");
        var start = new Coordinates(new BigDecimal("126.4724622276"), new BigDecimal("33.5093583102"));
        var end = new Coordinates(new BigDecimal("126.4640748143"), new BigDecimal("33.4788450641"));
        var result = new KakaoTransitClient(http, new KakaoTransitNormalizer(),
                "https://dapi.kakao.com", key).findRoutes(start, end);
        assertThat(result.status()).isEqualTo(TransitResult.Status.OK);
        assertThat(result.routes().size()).isPositive();
        assertThat(result.routes().stream().allMatch(r -> r.totalTimeMinutes().signum() > 0 && r.fareWon() > 0)).isTrue();
    }
}
