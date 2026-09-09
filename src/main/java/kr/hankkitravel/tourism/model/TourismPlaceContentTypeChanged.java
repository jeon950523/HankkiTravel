package kr.hankkitravel.tourism.model;

/** Published after a canonical cache type change so dependent specializations can remove stale meaning. */
public record TourismPlaceContentTypeChanged(long tourismPlaceId, String previousContentTypeId,
        String currentContentTypeId) {
    public boolean leftRestaurant() {
        return TourismContentType.RESTAURANT.code().equals(previousContentTypeId)
                && !TourismContentType.RESTAURANT.code().equals(currentContentTypeId);
    }
}
