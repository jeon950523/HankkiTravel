package kr.hankkitravel.tourism.model;

/** Current TourAPI restaurant fields. Values remain nullable when TourAPI does not provide them. */
public record TourismRestaurantDetail(String firstMenu, String treatMenu, String openTime,
        String restDate, String parking, String title, String address, String firstImage,
        String contentId, String contentType, String regionCode, String districtCode, String telephone) {
    public TourismRestaurantDetail(String firstMenu, String treatMenu, String openTime, String restDate, String parking,
            String title, String address, String firstImage, String contentId, String contentType, String regionCode, String districtCode) {
        this(firstMenu,treatMenu,openTime,restDate,parking,title,address,firstImage,contentId,contentType,regionCode,districtCode,null);
    }
    public TourismRestaurantDetail(String firstMenu, String treatMenu, String openTime,
            String restDate, String parking, String title, String address, String firstImage) {
        this(firstMenu, treatMenu, openTime, restDate, parking, title, address, firstImage, null, null, null, null, null);
    }
    public TourismRestaurantDetail(String firstMenu, String treatMenu, String openTime, String restDate, String parking) {
        this(firstMenu, treatMenu, openTime, restDate, parking, null, null, null);
    }

    public TourismRestaurantDetail withPresentation(TourismRestaurantPresentation presentation) {
        return new TourismRestaurantDetail(firstMenu, treatMenu, openTime, restDate, parking,
                presentation.title(), presentation.address(), presentation.firstImage(), presentation.contentId(),
                presentation.contentType(), presentation.regionCode(), presentation.districtCode(), presentation.telephone());
    }
}
