package kr.hankkitravel.tourism.model;

import kr.hankkitravel.shared.geo.Coordinates;

/** Current TourAPI place data held in memory only. */
public record TourismLivePlace(String contentId, String contentType, String title, String address,
        String firstImage, Coordinates coordinates, String regionCode, String districtCode) { }
