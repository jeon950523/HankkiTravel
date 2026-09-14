package kr.hankkitravel.restaurant.model;

public record RestaurantClassificationResult(RestaurantClassification classification, String reason) {
    public RestaurantClassificationResult {
        if (classification == null || reason == null || reason.isBlank()) throw new IllegalArgumentException("분류 근거가 필요합니다.");
    }
}
