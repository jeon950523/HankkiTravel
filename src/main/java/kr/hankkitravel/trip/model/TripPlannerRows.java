package kr.hankkitravel.trip.model;

import java.time.LocalDate;
import lombok.Data;

public final class TripPlannerRows {
    private TripPlannerRows() { }
    @Data public static class Context {
        private Long tripId; private Long guestId; private Long profileId; private Long dayId;
        private String tripPublicId; private String regionKey;
        private LocalDate startDate; private LocalDate endDate; private LocalDate travelDate;
        private int dayNumber;
    }
    @Data public static class Reference {
        private Long id; private String publicId; private Long tripDayId; private String slotType;
        private String provider; private String contentId; private String contentType;
    }
}
