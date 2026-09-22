package kr.hankkitravel.transit.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record KakaoRawResponse(String status, Properties properties, List<Route> routes) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Properties(String landingURL) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Route(RouteProperties properties, List<Step> steps) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RouteProperties(String type, Long totalDistance, Long totalTime, Integer transfers, Fare fare) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Fare(Long value) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Step(StepProperties properties) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record StepProperties(String type, Long distance, Long time, String guidance,
            List<Stop> stops, List<Vehicle> vehicles) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Stop(String name) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Vehicle(String name, String type) {}
}
