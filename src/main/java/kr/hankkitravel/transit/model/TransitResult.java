package kr.hankkitravel.transit.model;

import java.util.List;

public record TransitResult(Status status, List<TransitRoute> routes, String kakaoMapLandingUrl) {
    public enum Status { OK, NO_RESULTS }
    public TransitResult { routes = List.copyOf(routes); }
}
