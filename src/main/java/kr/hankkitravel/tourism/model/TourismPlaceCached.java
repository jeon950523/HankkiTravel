package kr.hankkitravel.tourism.model;

/** Published inside the tourism scope transaction after a restaurant cache mutation. */
public record TourismPlaceCached(long tourismPlaceId, TourismContentType contentType) {
    public boolean isRestaurant() { return contentType.isRestaurant(); }
}
