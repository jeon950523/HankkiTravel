package kr.hankkitravel.recommendation.application;

import java.util.List;
import kr.hankkitravel.shared.geo.Coordinates;

public interface ContactEnrichmentProvider {
    Result enrich(String name, String address, Coordinates coordinates);
    record Result(Status status, String phone, String placeUrl) {
        public static Result unavailable() { return new Result(Status.UNAVAILABLE, null, null); }
    }
    enum Status { MATCHED, AMBIGUOUS, REJECTED, UNAVAILABLE }
    record Place(String name, String phone, String address, String roadAddress,
            Coordinates coordinates, String placeUrl, String category) { }
    interface SearchSource { List<Place> search(String name, Coordinates coordinates); }
}
