package kr.hankkitravel.restaurant.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import kr.hankkitravel.tourism.model.TourismPlaceId;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Restaurant {
    private Long id;
    private Long tourismPlaceId;
    private Instant createdAt;
    private Instant updatedAt;

    public Restaurant(TourismPlaceId tourismPlaceId) {
        this.tourismPlaceId = java.util.Objects.requireNonNull(tourismPlaceId).value();
    }
}
