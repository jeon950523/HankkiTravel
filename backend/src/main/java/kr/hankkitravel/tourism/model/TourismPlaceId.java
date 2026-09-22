package kr.hankkitravel.tourism.model;

public record TourismPlaceId(long value) {
    public TourismPlaceId {
        if (value <= 0) throw new IllegalArgumentException("양수 내부 ID가 필요합니다.");
    }
}
