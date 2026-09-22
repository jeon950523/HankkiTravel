package kr.hankkitravel.tourism.model;

import kr.hankkitravel.shared.geo.Coordinates;

/** Normalized TourAPI list item. The source time values deliberately remain raw strings. */
public record TourismPlace(String contentId, String contentTypeId, String title, String address,
        String addressDetail, String tel, String zipcode, Coordinates coordinates, String firstImage,
        String firstImage2, String cpyrhtDivCd, String lDongRegnCd, String lDongSignguCd,
        String lclsSystm1, String lclsSystm2, String lclsSystm3, String createdTime,
        String modifiedTime, String showFlag) {

    /** P0.1 compatibility constructor used by the adapter smoke contract. */
    public TourismPlace(String contentId, String contentTypeId, String title,
            String address, Coordinates coordinates, String modifiedTime) {
        this(contentId, contentTypeId, title, address, null, null, null, coordinates, null, null,
                null, null, null, null, null, null, null, modifiedTime, null);
    }
}
