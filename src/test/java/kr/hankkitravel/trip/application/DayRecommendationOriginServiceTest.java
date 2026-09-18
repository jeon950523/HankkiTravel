package kr.hankkitravel.trip.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.tourism.application.TourismRealtimeGateway;
import kr.hankkitravel.tourism.model.TourismLivePlace;
import kr.hankkitravel.trip.model.TripPlannerRows;
import org.junit.jupiter.api.Test;

class DayRecommendationOriginServiceTest {
    private final TripPlaceScheduleService schedules=mock(TripPlaceScheduleService.class);
    private final TourismRealtimeGateway tourism=mock(TourismRealtimeGateway.class);
    private final DayRecommendationOriginService origins=new DayRecommendationOriginService(schedules,tourism,new DayRecommendationContextResolver());

    @Test void missingCoordinatesDegradeToEarlierAnchorWithoutInventingCoordinates(){
        var refs=refs(1,List.of(ref("LUNCH","2")),List.of(ref("DAY_FOCUS","1"),ref("POST_LUNCH_DESSERT","3")));
        stub("1",126.1);when(tourism.place("2")).thenThrow(new IllegalStateException());when(tourism.place("3")).thenReturn(live("3",null));
        var result=origins.resolve(refs,"DINNER");
        assertThat(result.originSlot()).isEqualTo("DAY_FOCUS");
        assertThat(result.origin()).isEqualTo(new Coordinates(new BigDecimal("126.1"),new BigDecimal("33.5")));
    }

    @Test void exposesPreviousStayAndCurrentFocusSeparatelyAndKeepsFocusAsFirstMealOrigin(){
        var previous=refs(1,List.of(),List.of(ref("STAY","9")));
        var current=refs(2,List.of(),List.of(ref("DAY_FOCUS","1")));
        when(schedules.references("guest","trip",1)).thenReturn(previous);
        when(schedules.references("guest","trip",2)).thenReturn(current);
        stub("9",129.0);stub("1",126.1);
        var result=origins.resolve("guest","trip",2,"BREAKFAST");
        assertThat(result.previousDayStay()).isEqualTo(new Coordinates(new BigDecimal("129.0"),new BigDecimal("33.5")));
        assertThat(result.currentDayFocus()).isEqualTo(new Coordinates(new BigDecimal("126.1"),new BigDecimal("33.5")));
        assertThat(result.originSlot()).isEqualTo("DAY_FOCUS");
        assertThat(result.detailCalls()).isEqualTo(2);
    }

    @Test void dinnerUsesLatestActivityOriginAndExposesItsTitle(){
        var refs=refs(1,List.of(ref("LUNCH","2")),List.of(ref("DAY_FOCUS","1"),ref("AFTERNOON_ACTIVITY","3")));
        when(tourism.place("1")).thenReturn(live("1","시작 장소",126.1));
        when(tourism.place("2")).thenReturn(live("2","점심 식당",126.2));
        when(tourism.place("3")).thenReturn(live("3","오후 명소",126.3));
        var result=origins.resolve(refs,"DINNER");
        assertThat(result.originSlot()).isEqualTo("AFTERNOON_ACTIVITY");
        assertThat(result.originTitle()).isEqualTo("오후 명소");
    }

    @Test void dinnerFallsBackToLunchWhenActivityIsMissing(){
        var refs=refs(1,List.of(ref("LUNCH","2")),List.of(ref("DAY_FOCUS","1")));
        when(tourism.place("1")).thenReturn(live("1","시작 장소",126.1));
        when(tourism.place("2")).thenReturn(live("2","점심 식당",126.2));
        var result=origins.resolve(refs,"DINNER");
        assertThat(result.originSlot()).isEqualTo("LUNCH");
        assertThat(result.originTitle()).isEqualTo("점심 식당");
    }

    private TripPlaceScheduleService.DayReferences refs(int day,List<TripPlannerRows.Reference> meals,List<TripPlannerRows.Reference> places){
        var context=new TripPlaceScheduleService.DayContext(1,day,"trip",3,"JEJU",LocalDate.of(2026,9,18),LocalDate.of(2026,9,19),day,LocalDate.of(2026,9,17+day));
        return new TripPlaceScheduleService.DayReferences(context,meals,places,List.of("BREAKFAST","LUNCH","DINNER"));
    }
    private TripPlannerRows.Reference ref(String slot,String id){var value=new TripPlannerRows.Reference();value.setSlotType(slot);value.setContentId(id);value.setContentType("12");return value;}
    private void stub(String id,double longitude){when(tourism.place(id)).thenReturn(live(id,new Coordinates(BigDecimal.valueOf(longitude),new BigDecimal("33.5"))));}
    private TourismLivePlace live(String id,String title,double longitude){return new TourismLivePlace(id,"12",title,"제주",null,new Coordinates(BigDecimal.valueOf(longitude),new BigDecimal("33.5")),"50","110");}
    private TourismLivePlace live(String id,Coordinates coordinates){return new TourismLivePlace(id,"12","place","제주",null,coordinates,"50","110");}
}
