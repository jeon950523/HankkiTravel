package kr.hankkitravel.trip.model;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;

/** Database-owned schedule/reference rows, never a response carrying internal IDs. */
public final class TripRows {
    private TripRows() { }
    @Data public static class Schedule {
        private Long id;
        private String publicId;
        private Long guestId;
        private Long profileId;
        private String regionKey;
        private LocalDate startDate;
        private LocalDate endDate;
        private Instant createdAt;
        private int mealSlotCount;
        private int selectedAnchorCount;
    }
    @Data public static class Day {
        private Long id;
        private Long tripId;
        private int dayNumber;
        private LocalDate travelDate;
    }
    @Data public static class Slot {
        private Long id;
        private String publicId;
        private Long tripDayId;
        private String mealType;
        private String provider;
        private String contentId;
        private String contentType;
    }
}
