package kr.hankkitravel.trip.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Validated local schedule only. No tourism payload or recommendation result. */
public record TripPlan(long profileId, String regionKey, LocalDate startDate, LocalDate endDate, List<Day> days) {
    public enum MealType { BREAKFAST, LUNCH, DINNER }
    public record Day(int dayNumber, List<MealType> mealTypes) { }

    public TripPlan {
        if (profileId <= 0) throw TripProblem.invalid("INVALID_PROFILE_ID");
        if (regionKey == null || !Set.of("JEJU", "GYEONGJU").contains(regionKey))
            throw TripProblem.invalid("TRIP_REGION_UNSUPPORTED");
        if (startDate == null || endDate == null || endDate.isBefore(startDate))
            throw TripProblem.invalid("TRIP_DATE_RANGE_INVALID");
        long duration = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (duration > 4) throw TripProblem.invalid("TRIP_DURATION_EXCEEDED");
        if (days == null || days.size() != duration) throw TripProblem.invalid("TRIP_DAY_CONFIGURATION_INVALID");
        var seen = new HashSet<Integer>();
        var normalized = new ArrayList<Day>();
        int slots = 0;
        for (Day day : days) {
            if (day == null || day.dayNumber < 1 || day.dayNumber > duration || !seen.add(day.dayNumber)
                    || day.mealTypes == null || day.mealTypes.stream().anyMatch(Objects::isNull))
                throw TripProblem.invalid("TRIP_DAY_CONFIGURATION_INVALID");
            if (new HashSet<>(day.mealTypes).size() != day.mealTypes.size())
                throw TripProblem.invalid("TRIP_MEAL_SLOT_DUPLICATED");
            slots += day.mealTypes.size();
            normalized.add(new Day(day.dayNumber, day.mealTypes.stream().sorted().toList()));
        }
        if (slots == 0) throw TripProblem.invalid("TRIP_MEAL_SLOT_REQUIRED");
        if (slots > 12) throw TripProblem.invalid("TRIP_MEAL_SLOT_LIMIT_EXCEEDED");
        days = normalized.stream().sorted(Comparator.comparingInt(Day::dayNumber)).toList();
    }
    public int durationDays() { return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1; }
}
