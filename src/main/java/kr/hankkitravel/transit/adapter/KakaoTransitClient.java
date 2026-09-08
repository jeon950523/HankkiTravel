package kr.hankkitravel.transit.adapter;

import java.net.URI;
import java.util.Map;
import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.shared.integration.ExternalHttpClient;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import kr.hankkitravel.transit.model.TransitResult;
import org.springframework.web.util.UriComponentsBuilder;

public final class KakaoTransitClient {
    private final ExternalHttpClient http;
    private final KakaoTransitNormalizer normalizer;
    private final String baseUrl;
    private final String apiKey;

    public KakaoTransitClient(ExternalHttpClient http, KakaoTransitNormalizer normalizer, String baseUrl, String apiKey) {
        this.http = http;
        this.normalizer = normalizer;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public TransitResult findRoutes(Coordinates start, Coordinates end) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IntegrationException("KAKAO", IntegrationFailure.SECRET_NOT_PRESENT);
        }
        URI uri = UriComponentsBuilder.fromUriString(baseUrl).path("/v2/routing/publictraffic")
                .queryParam("start_x", start.longitude().toPlainString())
                .queryParam("start_y", start.latitude().toPlainString())
                .queryParam("end_x", end.longitude().toPlainString())
                .queryParam("end_y", end.latitude().toPlainString()).build().toUri();
        return normalizer.normalize(http.get("KAKAO", uri, Map.of("Authorization", "KakaoAK " + apiKey)));
    }
}
