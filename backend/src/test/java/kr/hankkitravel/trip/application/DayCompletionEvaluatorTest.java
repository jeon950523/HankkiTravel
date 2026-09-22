package kr.hankkitravel.trip.application;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import java.util.List;
import kr.hankkitravel.trip.model.TripPlannerRows;
import org.junit.jupiter.api.Test;

class DayCompletionEvaluatorTest {
    private final DayCompletionEvaluator evaluator=new DayCompletionEvaluator();

    @Test void optionalAnchorsDoNotBlockRequiredCompletion(){
        var context=new TripPlaceScheduleService.DayContext(1,2,"trip",3,"JEJU",LocalDate.of(2026,9,18),LocalDate.of(2026,9,19),1,LocalDate.of(2026,9,18));
        var result=evaluator.evaluate(new TripPlaceScheduleService.DayReferences(context,List.of(ref("LUNCH"),ref("DINNER")),List.of(ref("STAY")),List.of("LUNCH","DINNER")));
        assertThat(result.requiredComplete()).isTrue();
        assertThat(result.completedMealSlots()).isEqualTo(2);
        assertThat(result.stayCompleted()).isTrue();
    }

    @Test void lastDayDoesNotRequireStay(){
        var context=new TripPlaceScheduleService.DayContext(1,2,"trip",3,"JEJU",LocalDate.of(2026,9,18),LocalDate.of(2026,9,19),2,LocalDate.of(2026,9,19));
        var result=evaluator.evaluate(new TripPlaceScheduleService.DayReferences(context,List.of(ref("LUNCH")),List.of(),List.of("LUNCH")));
        assertThat(result.stayRequired()).isFalse();
        assertThat(result.requiredComplete()).isTrue();
    }

    private TripPlannerRows.Reference ref(String slot){var value=new TripPlannerRows.Reference();value.setSlotType(slot);value.setContentId("1");return value;}
}
