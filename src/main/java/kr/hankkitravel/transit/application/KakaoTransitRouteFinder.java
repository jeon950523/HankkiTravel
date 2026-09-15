package kr.hankkitravel.transit.application;

import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.transit.adapter.KakaoTransitClient;
import kr.hankkitravel.transit.model.TransitResult;
import org.springframework.stereotype.Component;

@Component
class KakaoTransitRouteFinder implements TransitRouteFinder {
    private final KakaoTransitClient client;

    KakaoTransitRouteFinder(KakaoTransitClient client) { this.client = client; }

    @Override
    public TransitResult findRoutes(Coordinates start, Coordinates end) {
        return client.findRoutes(start, end);
    }
}
