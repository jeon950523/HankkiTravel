package kr.hankkitravel.trip.application;

import java.util.HashSet;
import org.springframework.stereotype.Component;

/** Completion is derived from persisted anchors; no completion snapshot is stored. */
@Component
public final class DayCompletionEvaluator {
    public TripView.DayCompletion evaluate(TripPlaceScheduleService.DayReferences refs) {
        var completedMeals = new HashSet<String>();
        refs.meals().forEach(value -> completedMeals.add(value.getSlotType()));
        int requiredMeals = refs.requiredMealTypes().size();
        int completedMealCount = (int) refs.requiredMealTypes().stream().filter(completedMeals::contains).count();
        boolean stayRequired = !refs.context().lastDay();
        boolean stayCompleted = refs.places().stream().anyMatch(value -> "STAY".equals(value.getSlotType()));
        return new TripView.DayCompletion(requiredMeals, completedMealCount, stayRequired, stayCompleted,
                completedMealCount == requiredMeals && (!stayRequired || stayCompleted));
    }
}
