package kr.hankkitravel.tourism.model;

import kr.hankkitravel.shared.geo.Coordinates;

public record TourismPlace(String contentId, String contentTypeId, String title,
        String address, Coordinates coordinates, String modifiedTime) {}
