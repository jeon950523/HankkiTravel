package kr.hankkitravel.tourism.model;

import java.util.List;

/** Result of pure restaurant classification and menu normalization for live data. */
public record TourismRestaurantRuntimeData(String classification, String reason, List<TourismMenuCandidate> menuCandidates) {
    public TourismRestaurantRuntimeData {
        if (classification == null || classification.isBlank() || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("음식점 분류 근거가 필요합니다.");
        }
        menuCandidates = List.copyOf(menuCandidates);
    }
}
