package kr.hankkitravel.trip.model;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static kr.hankkitravel.trip.model.TripPlan.MealType.*;

class TripPlanTest {
    private final LocalDate start=LocalDate.of(2026,9,20);
    private TripPlan plan(int days,List<TripPlan.Day> entries) { return new TripPlan(1,"JEJU",start,start.plusDays(days-1),entries); }
    @ParameterizedTest @ValueSource(ints={1,2,3,4})
    void inclusiveDurationsAndMaximumSlots(int n) {
        var p=plan(n,IntStream.rangeClosed(1,n).mapToObj(i -> new TripPlan.Day(i,List.of(DINNER,LUNCH,BREAKFAST))).toList());
        assertThat(p.durationDays()).isEqualTo(n);
        assertThat(p.days().stream().mapToInt(d -> d.mealTypes().size()).sum()).isEqualTo(n*3);
        assertThat(p.days().getFirst().mealTypes()).containsExactly(BREAKFAST,LUNCH,DINNER);
    }
    @Test void retainsZeroMealDaysAndSortsDays() {
        var p=plan(2,List.of(new TripPlan.Day(2,List.of(LUNCH)),new TripPlan.Day(1,List.of())));
        assertThat(p.days()).extracting(TripPlan.Day::dayNumber).containsExactly(1,2);
        assertThat(p.days().getFirst().mealTypes()).isEmpty();
    }
    @Test void rejectsFiveDays() { bad(() -> plan(5,List.of()),"TRIP_DURATION_EXCEEDED"); }
    @Test void rejectsReversedDates() { bad(() -> plan(0,List.of()),"TRIP_DATE_RANGE_INVALID"); }
    @Test void rejectsMissingDay() { bad(() -> plan(2,List.of(new TripPlan.Day(1,List.of(LUNCH)))),"TRIP_DAY_CONFIGURATION_INVALID"); }
    @Test void rejectsDuplicateDay() { bad(() -> plan(2,List.of(new TripPlan.Day(1,List.of(LUNCH)),new TripPlan.Day(1,List.of(DINNER)))),"TRIP_DAY_CONFIGURATION_INVALID"); }
    @Test void rejectsDuplicateMeal() { bad(() -> plan(1,List.of(new TripPlan.Day(1,List.of(LUNCH,LUNCH)))),"TRIP_MEAL_SLOT_DUPLICATED"); }
    @Test void rejectsZeroSlots() { bad(() -> plan(1,List.of(new TripPlan.Day(1,List.of()))),"TRIP_MEAL_SLOT_REQUIRED"); }
    @Test void rejectsUnsupportedRegion() { bad(() -> new TripPlan(1,"DANYANG",start,start,List.of()),"TRIP_REGION_UNSUPPORTED"); }
    @Test void rejectsInvalidProfile() { bad(() -> new TripPlan(0,"JEJU",start,start,List.of()),"INVALID_PROFILE_ID"); }
    @Test void rejectsNullMealAndOutOfRangeDay() {
        bad(() -> plan(1,List.of(new TripPlan.Day(1,Arrays.asList((TripPlan.MealType)null)))),"TRIP_DAY_CONFIGURATION_INVALID");
        bad(() -> plan(1,List.of(new TripPlan.Day(2,List.of(LUNCH)))),"TRIP_DAY_CONFIGURATION_INVALID");
    }
    private void bad(Runnable action,String code) { assertThatThrownBy(action::run).isInstanceOfSatisfying(TripProblem.class,e -> assertThat(e.code()).isEqualTo(code)); }
}
