package kr.hankkitravel.trip.application;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import java.util.List;
import kr.hankkitravel.trip.model.TripPlannerRows;
import org.junit.jupiter.api.Test;

class DayRecommendationContextResolverTest {
    private final DayRecommendationContextResolver resolver=new DayRecommendationContextResolver();

    @Test void resolvesMostRecentAnchorBeforeEachTargetDeterministically(){
        var refs=refs(2,List.of(ref("BREAKFAST","2"),ref("LUNCH","4")),List.of(ref("DAY_FOCUS","1"),ref("MORNING_ACTIVITY","3"),ref("POST_LUNCH_DESSERT","5"),ref("AFTERNOON_ACTIVITY","6")));
        assertThat(slots(resolver.resolve(refs,"BREAKFAST"))).containsExactly("DAY_FOCUS");
        assertThat(slots(resolver.resolve(refs,"LUNCH"))).startsWith("MORNING_ACTIVITY","BREAKFAST","DAY_FOCUS");
        assertThat(slots(resolver.resolve(refs,"DINNER"))).startsWith("AFTERNOON_ACTIVITY","POST_LUNCH_DESSERT","LUNCH");
        assertThat(slots(resolver.resolve(refs,"STAY"))).startsWith("AFTERNOON_ACTIVITY","POST_LUNCH_DESSERT","LUNCH");
    }

    @Test void separatesPreviousStayFromCurrentFocus(){
        var previous=refs(1,List.of(),List.of(ref("STAY","9")));
        var current=refs(2,List.of(),List.of(ref("DAY_FOCUS","1")));
        var result=resolver.resolve(previous,current,"BREAKFAST");
        assertThat(result.previousDayStay().getContentId()).isEqualTo("9");
        assertThat(result.currentDayFocus().getContentId()).isEqualTo("1");
        assertThat(result.previousDayStay()).isNotEqualTo(result.currentDayFocus());
    }

    private TripPlaceScheduleService.DayReferences refs(int day,List<TripPlannerRows.Reference> meals,List<TripPlannerRows.Reference> places){
        var context=new TripPlaceScheduleService.DayContext(1,day,"trip",3,"JEJU",LocalDate.of(2026,9,18),LocalDate.of(2026,9,19),day,LocalDate.of(2026,9,17+day));
        return new TripPlaceScheduleService.DayReferences(context,meals,places,List.of("BREAKFAST","LUNCH","DINNER"));
    }
    private TripPlannerRows.Reference ref(String slot,String id){var value=new TripPlannerRows.Reference();value.setSlotType(slot);value.setContentId(id);value.setContentType("12");return value;}
    private List<String> slots(DayRecommendationContextResolver.Context context){return context.originCandidates().stream().map(TripPlannerRows.Reference::getSlotType).toList();}
}
