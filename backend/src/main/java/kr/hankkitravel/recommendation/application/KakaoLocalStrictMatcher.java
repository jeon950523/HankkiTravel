package kr.hankkitravel.recommendation.application;

import java.util.List;
import java.util.Locale;
import kr.hankkitravel.shared.geo.Coordinates;

public final class KakaoLocalStrictMatcher {
    private final double maxDistanceMeters;
    public KakaoLocalStrictMatcher(double maxDistanceMeters) {
        if (maxDistanceMeters <= 0 || maxDistanceMeters > 1000) throw new IllegalArgumentException("장소 매칭 거리 기준을 확인하세요.");
        this.maxDistanceMeters = maxDistanceMeters;
    }
    public ContactEnrichmentProvider.Result match(String name, String address, Coordinates coordinates,
            List<ContactEnrichmentProvider.Place> places) {
        var matches = places.stream().filter(place -> restaurant(place.category()))
                .filter(place -> normalize(place.name()).equals(normalize(name)))
                .filter(place -> addressMatches(address, place.address(), place.roadAddress())
                        || close(coordinates, place.coordinates())).toList();
        if (matches.size() > 1) return new ContactEnrichmentProvider.Result(ContactEnrichmentProvider.Status.AMBIGUOUS, null, null);
        if (matches.isEmpty()) return new ContactEnrichmentProvider.Result(ContactEnrichmentProvider.Status.REJECTED, null, null);
        var match = matches.getFirst();
        return new ContactEnrichmentProvider.Result(ContactEnrichmentProvider.Status.MATCHED,
                blankToNull(match.phone()), blankToNull(match.placeUrl()));
    }
    private boolean restaurant(String category) { return category != null && category.contains("음식점"); }
    private boolean addressMatches(String expected, String first, String second) {
        String target = normalizeAddress(expected);
        if (target.length() < 8) return false;
        return List.of(first, second).stream().map(KakaoLocalStrictMatcher::normalizeAddress)
                .anyMatch(value -> !value.isBlank() && (value.contains(target) || target.contains(value)));
    }
    private boolean close(Coordinates left, Coordinates right) {
        if (left == null || right == null) return false;
        double lat1=Math.toRadians(left.latitude().doubleValue()), lat2=Math.toRadians(right.latitude().doubleValue());
        double dlat=lat2-lat1, dlon=Math.toRadians(right.longitude().doubleValue()-left.longitude().doubleValue());
        double h=Math.sin(dlat/2)*Math.sin(dlat/2)+Math.cos(lat1)*Math.cos(lat2)*Math.sin(dlon/2)*Math.sin(dlon/2);
        return 6371000*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h)) <= maxDistanceMeters;
    }
    private static String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-z가-힣]", ""); }
    private static String normalizeAddress(String value) { return normalize(value).replace("대한민국", ""); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
