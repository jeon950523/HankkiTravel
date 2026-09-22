package kr.hankkitravel.tourism.model;

/** Current basic presentation fields from TourAPI detailCommon2. */
public record TourismRestaurantPresentation(String title, String address, String firstImage,
        String contentId, String contentType, String regionCode, String districtCode, String telephone) {
    public TourismRestaurantPresentation(String title, String address, String firstImage,
            String contentId, String contentType, String regionCode, String districtCode) {
        this(title,address,firstImage,contentId,contentType,regionCode,districtCode,null);
    }
    public TourismRestaurantPresentation(String title, String address, String firstImage) {
        this(title, address, firstImage, null, null, null, null, null);
    }
}
