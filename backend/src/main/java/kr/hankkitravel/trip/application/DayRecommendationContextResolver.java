package kr.hankkitravel.trip.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import kr.hankkitravel.trip.model.TripPlannerRows;
import org.springframework.stereotype.Component;

/** Resolves persisted anchor chronology only. Live place hydration belongs to the origin service. */
@Component
public final class DayRecommendationContextResolver {
    public Context resolve(TripPlaceScheduleService.DayReferences refs, String targetSlot) {
        var candidates = new ArrayList<TripPlannerRows.Reference>();
        candidates.addAll(refs.meals());
        candidates.addAll(refs.places().stream().filter(value -> !"STAY".equals(value.getSlotType())).toList());
        int targetOrder = order(targetSlot);
        var ordered = candidates.stream()
                .filter(value -> order(value.getSlotType()) < targetOrder)
                .sorted(Comparator.comparingInt((TripPlannerRows.Reference value) -> order(value.getSlotType())).reversed()
                        .thenComparing(TripPlannerRows.Reference::getContentId, Comparator.nullsLast(String::compareTo)))
                .toList();
        return new Context(ordered, reference(refs, "DAY_FOCUS"), null);
    }

    public Context resolve(TripPlaceScheduleService.DayReferences previousDay,
            TripPlaceScheduleService.DayReferences currentDay, String targetSlot) {
        var current = resolve(currentDay, targetSlot);
        return new Context(current.originCandidates(), current.currentDayFocus(), reference(previousDay, "STAY"));
    }

    static int order(String slot) {
        if (slot == null) return 99;
        return switch (slot) {
            case "DAY_FOCUS" -> 0;
            case "BREAKFAST" -> 1;
            case "MORNING_ACTIVITY" -> 2;
            case "LUNCH" -> 3;
            case "POST_LUNCH_DESSERT" -> 4;
            case "AFTERNOON_ACTIVITY" -> 5;
            case "DINNER" -> 6;
            case "POST_DINNER_DESSERT", "POST_MEAL_DESSERT" -> 7;
            case "STAY" -> 8;
            default -> 99;
        };
    }

    public static String label(String slot) {
        if (slot == null) return "기준 장소";
        return switch (slot) {
            case "DAY_FOCUS" -> "오늘 여행의 시작 장소";
            case "BREAKFAST" -> "아침 식당";
            case "MORNING_ACTIVITY" -> "오전 관광";
            case "LUNCH" -> "점심 식당";
            case "POST_LUNCH_DESSERT" -> "점심 후 디저트";
            case "AFTERNOON_ACTIVITY" -> "오후 관광";
            case "DINNER" -> "저녁 식당";
            case "POST_DINNER_DESSERT", "POST_MEAL_DESSERT" -> "저녁 후 디저트";
            case "STAY" -> "숙소";
            default -> "기준 장소";
        };
    }

    private TripPlannerRows.Reference reference(TripPlaceScheduleService.DayReferences refs, String slot) {
        if (refs == null) return null;
        return refs.places().stream().filter(value -> slot.equals(value.getSlotType())).findFirst().orElse(null);
    }

    public record Context(List<TripPlannerRows.Reference> originCandidates,
            TripPlannerRows.Reference currentDayFocus, TripPlannerRows.Reference previousDayStay) { }
}
