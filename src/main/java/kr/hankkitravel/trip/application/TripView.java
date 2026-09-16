package kr.hankkitravel.trip.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TripView(String tripPublicId, long profileId, String regionKey, LocalDate startDate,
        LocalDate endDate, int durationDays, List<Day> days) {
    public record Day(int dayNumber, LocalDate travelDate, List<Slot> mealSlots) { }
    public record Slot(String mealSlotPublicId, String mealType, Anchor anchor) { }
    public record Anchor(String provider, String contentId, String contentType) { }
    public record Summary(String tripPublicId, String regionKey, LocalDate startDate, LocalDate endDate,
            int durationDays, long profileId, int mealSlotCount, int selectedAnchorCount, Instant createdAt) { }
}
