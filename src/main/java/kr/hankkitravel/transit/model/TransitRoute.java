package kr.hankkitravel.transit.model;

import java.math.BigDecimal;
import java.util.List;

public record TransitRoute(BigDecimal totalTimeMinutes, long totalDistanceMeters, int transferCount,
        long fareWon, String routeType, List<Segment> segments, long explicitWalkingDistanceMeters,
        long unaccountedDistanceMeters, String kakaoMapLandingUrl) {
    public TransitRoute { segments = List.copyOf(segments); }
    public record Segment(String type, long distanceMeters, long timeSeconds, String guidance,
            List<Stop> stops, List<Vehicle> vehicles) {
        public Segment { stops = List.copyOf(stops); vehicles = List.copyOf(vehicles); }
    }
    public record Stop(String name) {}
    public record Vehicle(String name, String type) {}
}
