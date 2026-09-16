package kr.hankkitravel.trip.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import kr.hankkitravel.profile.application.FamilyProfileApplicationService;
import kr.hankkitravel.profile.application.FamilyProfileSnapshot;
import kr.hankkitravel.recommendation.application.AreaDemandSignalProvider;
import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import kr.hankkitravel.tourism.application.TourismRealtimeGateway;
import kr.hankkitravel.tourism.model.TourApiPage;
import kr.hankkitravel.tourism.model.TourismContentType;
import kr.hankkitravel.tourism.model.TourismLivePlace;
import kr.hankkitravel.tourism.model.TourismPlace;
import kr.hankkitravel.tourism.model.TourismRegion;
import kr.hankkitravel.transit.application.TransitRouteFinder;
import kr.hankkitravel.transit.model.TransitResult;
import kr.hankkitravel.transit.model.TransitRoute;
import kr.hankkitravel.trip.model.TripPlannerRows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RouteAwareAttractionRecommendationServiceTest {
    TripPlaceScheduleService schedules=mock(TripPlaceScheduleService.class);
    FamilyProfileApplicationService profiles=mock(FamilyProfileApplicationService.class);
    TourismRealtimeGateway tourism=mock(TourismRealtimeGateway.class);
    TransitRouteFinder transit=mock(TransitRouteFinder.class);
    AreaDemandSignalProvider demand=mock(AreaDemandSignalProvider.class);
    RouteAwareAttractionRecommendationService service;

    @BeforeEach void setUp(){
        service=new RouteAwareAttractionRecommendationService(schedules,profiles,tourism,transit,demand,15,6,2,90,25,150,50,20,15,15,20,15,40,25);
        var context=new TripPlaceScheduleService.DayContext(1,2,"trip",3,"JEJU",LocalDate.of(2026,9,20),LocalDate.of(2026,9,20),1,LocalDate.of(2026,9,20));
        when(schedules.references("guest","trip",1)).thenReturn(new TripPlaceScheduleService.DayReferences(context,List.of(ref("LUNCH","39001")),List.of()));
        when(profiles.owned("guest",3)).thenReturn(profile("PUBLIC_TRANSIT"));
        when(tourism.places(any(TourismRegion.class),any(TourismContentType.class),anyInt(),anyInt()))
                .thenReturn(page(List.of(place("1",126.501),place("2",126.510),place("3",126.520))),page(List.of()));
        when(tourism.place(any())).thenAnswer(inv->{String id=inv.getArgument(0);return "39001".equals(id)?live(id,126.500,"39"):live(id,126.500+Integer.parseInt(id)*.01,"12");});
        when(demand.signal(any(),any())).thenReturn(new AreaDemandSignalProvider.Signal(true,75,"202508","공식 관광 수요를 확인했어요.",true,true,List.of("공식"),1,1));
        when(transit.findRoutes(any(),any())).thenReturn(route(12,0,120));
    }

    @Test void transitNearbyRankingUsesBoundedCallsAndOnlyExplicitWalking(){
        var result=service.recommend("guest","trip",1,TripPlannerView.SlotType.AFTERNOON_ACTIVITY);
        var nearby=result.perspectives().getFirst().candidates();
        assertThat(nearby.getFirst().contentId()).isEqualTo("1");
        assertThat(nearby.getFirst().transitSummary().explicitWalkingDistanceMeters()).isEqualTo(120);
        assertThat(result.callSummary().transitCalls()).isEqualTo(2);verify(transit,times(2)).findRoutes(any(),any());
    }

    @Test void selectedDayFocusIsExcludedFromBothPerspectives(){
        var context=schedules.references("guest","trip",1).context();
        when(schedules.references("guest","trip",1)).thenReturn(new TripPlaceScheduleService.DayReferences(context,List.of(ref("LUNCH","39001")),List.of(ref("DAY_FOCUS","1"))));
        var result=service.recommend("guest","trip",1,TripPlannerView.SlotType.AFTERNOON_ACTIVITY);
        assertThat(result.perspectives()).allSatisfy(group->assertThat(group.candidates()).extracting(TripPlannerView.Candidate::contentId).doesNotContain("1"));
    }

    @Test void kakaoFailureDegradesAndCarUsesStraightLineReferenceWithoutTransit(){
        when(transit.findRoutes(any(),any())).thenThrow(new IntegrationException("KAKAO",IntegrationFailure.NETWORK_FAILURE));
        var degraded=service.recommend("guest","trip",1,TripPlannerView.SlotType.AFTERNOON_ACTIVITY);
        assertThat(degraded.dataAvailability()).isEqualTo("TRANSIT_PARTIALLY_UNAVAILABLE");
        assertThat(degraded.candidates()).allSatisfy(candidate->assertThat(candidate.routeBurden().state()).isEqualTo("NOT_EVALUATED"));

        when(profiles.owned("guest",3)).thenReturn(profile("CAR"));
        var car=service.recommend("guest","trip",1,TripPlannerView.SlotType.AFTERNOON_ACTIVITY);
        assertThat(car.candidates()).allSatisfy(candidate->{assertThat(candidate.routeBurden().state()).isEqualTo("REFERENCE");
            assertThat(candidate.transitSummary()).isNull();assertThat(candidate.cautions()).anyMatch(value->value.contains("실제 도로 이동시간"));});
    }

    private FamilyProfileSnapshot profile(String mode){return new FamilyProfileSnapshot(3,"family",mode,"NO_PREFERENCE","LOW","AVOID",false,
            List.of(new FamilyProfileSnapshot.Member(1,"member",20,"NEUTRAL",List.of("NONE"),false,List.of(),List.of())));}
    private TripPlannerRows.Reference ref(String slot,String id){var value=new TripPlannerRows.Reference();value.setSlotType(slot);value.setContentId(id);value.setContentType(id.startsWith("39")?"39":"12");return value;}
    private TourApiPage page(List<TourismPlace> values){return new TourApiPage(values,1,15,values.size());}
    private TourismPlace place(String id,double longitude){return new TourismPlace(id,"12","place-"+id,"제주 주소",null,null,null,
            new Coordinates(BigDecimal.valueOf(longitude),new BigDecimal("33.5")),null,null,null,"50","110","A",null,null,null,null,null);}
    private TourismLivePlace live(String id,double longitude,String type){return new TourismLivePlace(id,type,"live-"+id,"제주 주소",null,
            new Coordinates(BigDecimal.valueOf(longitude),new BigDecimal("33.5")),"50","110");}
    private TransitResult route(int minutes,int transfers,long walking){return new TransitResult(TransitResult.Status.OK,List.of(new TransitRoute(
            BigDecimal.valueOf(minutes),5000,transfers,1500,"PUBLIC_TRANSIT",List.of(),walking,9999,"https://map.kakao.test")),"https://map.kakao.test");}
}
