package kr.hankkitravel.transit.adapter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import kr.hankkitravel.transit.model.TransitResult;
import kr.hankkitravel.transit.model.TransitRoute;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class KakaoTransitNormalizer {
    private final JsonMapper mapper = JsonMapper.builder().build();

    public TransitResult normalize(String json) {
        try {
            var raw = mapper.readValue(json, KakaoRawResponse.class);
            require(raw != null && raw.status() != null);
            String landing = raw.properties() == null ? null : safeLanding(raw.properties().landingURL());
            if ("NO_RESULTS".equals(raw.status())) {
                return new TransitResult(TransitResult.Status.NO_RESULTS, List.of(), landing);
            }
            if (!"OK".equals(raw.status())) {
                throw new IntegrationException("KAKAO", IntegrationFailure.UPSTREAM_REJECTED);
            }
            require(raw.routes() != null);
            var routes = raw.routes().stream().map(route -> normalizeRoute(route, landing)).toList();
            return new TransitResult(routes.isEmpty() ? TransitResult.Status.NO_RESULTS : TransitResult.Status.OK,
                    routes, landing);
        } catch (JacksonException | IllegalArgumentException | ArithmeticException e) {
            throw new IntegrationException("KAKAO", IntegrationFailure.JSON_PARSING_FAILURE);
        }
    }

    private TransitRoute normalizeRoute(KakaoRawResponse.Route route, String landing) {
        require(route != null && route.properties() != null && route.steps() != null);
        var p = route.properties();
        require(p.type() != null && p.transfers() != null && p.transfers() >= 0 && p.fare() != null);
        long total = nonNegative(p.totalDistance());
        long seconds = nonNegative(p.totalTime());
        long stepDistance = 0;
        long walking = 0;
        var segments = new ArrayList<TransitRoute.Segment>();
        for (var step : route.steps()) {
            require(step != null && step.properties() != null);
            var s = step.properties();
            require(s.type() != null && !s.type().isBlank());
            long distance = nonNegative(s.distance());
            stepDistance = Math.addExact(stepDistance, distance);
            if ("WALKING".equals(s.type())) walking = Math.addExact(walking, distance);
            var stops = new ArrayList<TransitRoute.Stop>();
            for (var stop : s.stops() == null ? List.<KakaoRawResponse.Stop>of() : s.stops()) {
                require(stop != null);
                stops.add(new TransitRoute.Stop(stop.name()));
            }
            var vehicles = new ArrayList<TransitRoute.Vehicle>();
            for (var vehicle : s.vehicles() == null ? List.<KakaoRawResponse.Vehicle>of() : s.vehicles()) {
                require(vehicle != null);
                vehicles.add(new TransitRoute.Vehicle(vehicle.name(), vehicle.type()));
            }
            segments.add(new TransitRoute.Segment(s.type(), distance, nonNegative(s.time()),
                    s.guidance(), stops, vehicles));
        }
        return new TransitRoute(BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP),
                total, p.transfers(), nonNegative(p.fare().value()), p.type(), segments, walking,
                Math.max(total - stepDistance, 0), landing);
    }

    private long nonNegative(Long value) {
        require(value != null && value >= 0);
        return value;
    }

    private void require(boolean valid) {
        if (!valid) throw new IllegalArgumentException("Invalid upstream structure");
    }

    private String safeLanding(String value) {
        if (value == null || value.isBlank()) return null;
        var uri = URI.create(value);
        return "https".equals(uri.getScheme()) && "map.kakao.com".equals(uri.getHost())
                && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443) ? value : null;
    }
}
