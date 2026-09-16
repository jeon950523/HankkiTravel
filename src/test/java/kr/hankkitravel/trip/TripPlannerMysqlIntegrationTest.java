package kr.hankkitravel.trip;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;
import kr.hankkitravel.identity.application.GuestApplicationService;
import kr.hankkitravel.profile.application.FamilyProfileApplicationService;
import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.shared.integration.*;
import kr.hankkitravel.tourism.application.TourismRealtimeSource;
import kr.hankkitravel.tourism.model.*;
import kr.hankkitravel.transit.application.TransitRouteFinder;
import kr.hankkitravel.transit.model.*;
import kr.hankkitravel.trip.application.*;
import kr.hankkitravel.trip.model.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class TripPlannerMysqlIntegrationTest {
    @Container static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.4")
            .withDatabaseName("planner_test").withUsername("planner_test").withPassword(UUID.randomUUID().toString());
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);
        r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");}
    @Autowired GuestApplicationService guests;@Autowired FamilyProfileApplicationService profiles;
    @Autowired TripScheduleService trips;@Autowired TripPlannerService planner;@Autowired TripPlaceScheduleService places;
    @Autowired JdbcTemplate jdbc;@Autowired Flyway flyway;@MockitoBean TourismRealtimeSource source;
    @MockitoBean TransitRouteFinder transit;@LocalServerPort int port;
    String guest;long profile;TripView trip;final LocalDate start=LocalDate.of(2026,9,20);
    @BeforeEach void prepare(){
        guest=guests.create().getPublicId();profile=profiles.create(guest,new FamilyProfileApplicationService.ProfileCommand(
                "planner family","PUBLIC_TRANSIT","NO_PREFERENCE","NORMAL","AVOID",false,
                List.of(new FamilyProfileApplicationService.MemberCommand("member",30,"NEUTRAL",List.of("NONE"))))).profileId();
        trip=trips.create(guest,new TripPlan(profile,"JEJU",start,start.plusDays(1),List.of(
                new TripPlan.Day(1,List.of(TripPlan.MealType.BREAKFAST,TripPlan.MealType.LUNCH,TripPlan.MealType.DINNER)),
                new TripPlan.Day(2,List.of(TripPlan.MealType.LUNCH)))));
        for(var slot:trip.days().getFirst().mealSlots())trips.select(guest,trip.tripPublicId(),slot.mealSlotPublicId(),
                new TripScheduleService.ValidatedAnchor(switch(slot.mealType()){case"BREAKFAST"->"39001";case"LUNCH"->"39002";default->"39003";},"39"));
        when(source.fetchPlacePage(any(),any(),anyInt(),anyInt())).thenAnswer(inv->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            TourismRegion region=inv.getArgument(0);TourismContentType type=inv.getArgument(1);List<TourismPlace> list;
            if(type==TourismContentType.ATTRACTION)list=List.of(place(region==TourismRegion.JEJU_CITY?"12001":"12002","12",region));
            else list=region==TourismRegion.JEJU_CITY?List.of(place("32001","32",region)):List.of();
            return new TourApiPage(list,1,15,list.size());
        });
        when(source.searchPlacePage(any(),any(),anyString(),anyInt(),anyInt())).thenAnswer(inv->{
            TourismRegion region=inv.getArgument(0);var item=place(region==TourismRegion.JEJU_CITY?"12001":"12002","12",region);
            return new TourApiPage(List.of(item),1,15,1);
        });
        when(source.fetchPlaceDetail(anyString())).thenAnswer(inv->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return live(inv.getArgument(0));});
        when(transit.findRoutes(any(),any())).thenAnswer(inv->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new TransitResult(TransitResult.Status.OK,List.of(new TransitRoute(new BigDecimal("25.5"),5000,1,1500,"PUBLIC_TRANSIT",List.of(),500,900,"https://map.kakao.test")),"https://map.kakao.test");});
    }
    @Test void recommendationsUseOnlyLiveBoundaryDespiteStaleCacheRows(){
        long stale=seedStale();long beforePlaces=count("tourism_places"),beforeRestaurants=count("restaurants");
        var morning=planner.recommendActivities(guest,trip.tripPublicId(),1,TripPlannerView.SlotType.MORNING_ACTIVITY);
        assertThat(morning.candidates()).extracting(TripPlannerView.Candidate::contentId).containsExactly("12001","12002").doesNotContain("99999");
        assertThat(morning.candidates()).allSatisfy(c->{assertThat(c.informationEvidence()).isEqualTo("TOUR_API_LIVE");assertThat(c.sourceAttribution()).contains("한국관광공사");});
        var stay=planner.recommendStay(guest,trip.tripPublicId(),1);assertThat(stay.candidates()).extracting(TripPlannerView.Candidate::contentId).containsExactly("32001");
        assertThat(count("tourism_places")).isEqualTo(beforePlaces);assertThat(count("restaurants")).isEqualTo(beforeRestaurants);
        jdbc.update("DELETE FROM restaurants WHERE tourism_place_id=?",stale);jdbc.update("DELETE FROM tourism_places WHERE id=?",stale);
    }
    @Test void selectReplaceClearOwnershipAndStayRules(){
        var selected=planner.select(guest,trip.tripPublicId(),1,TripPlannerView.SlotType.MORNING_ACTIVITY,"12001");
        var repeated=planner.select(guest,trip.tripPublicId(),1,TripPlannerView.SlotType.MORNING_ACTIVITY,"12001");
        assertThat(repeated.publicId()).isEqualTo(selected.publicId());
        assertThat(planner.select(guest,trip.tripPublicId(),1,TripPlannerView.SlotType.MORNING_ACTIVITY,"12002").contentId()).isEqualTo("12002");
        planner.select(guest,trip.tripPublicId(),1,TripPlannerView.SlotType.STAY,"32001");
        assertThatThrownBy(()->planner.select(guest,trip.tripPublicId(),2,TripPlannerView.SlotType.STAY,"32001"))
                .isInstanceOf(TripProblem.class).hasMessageContaining("STAY_NOT_REQUIRED");
        assertThatThrownBy(()->planner.recommendStay(guest,trip.tripPublicId(),2)).isInstanceOf(TripProblem.class);
        planner.clear(guest,trip.tripPublicId(),2,TripPlannerView.SlotType.STAY);
        String other=guests.create().getPublicId();assertThatThrownBy(()->planner.planner(other,trip.tripPublicId(),1)).isInstanceOf(TripProblem.class);
        planner.clear(guest,trip.tripPublicId(),1,TripPlannerView.SlotType.MORNING_ACTIVITY);
        planner.clear(guest,trip.tripPublicId(),1,TripPlannerView.SlotType.MORNING_ACTIVITY);
    }
    @Test void dayFocusRecommendSearchSelectClearAndPlannerDeduplicateWork() throws Exception {
        assertThat(planner.recommendFocus(guest,trip.tripPublicId(),1).candidates()).isNotEmpty();
        assertThat(planner.searchFocus(guest,trip.tripPublicId(),1,"성산").candidates()).isNotEmpty();
        planner.select(guest,trip.tripPublicId(),1,TripPlannerView.SlotType.DAY_FOCUS,"12001");
        planner.select(guest,trip.tripPublicId(),1,TripPlannerView.SlotType.MORNING_ACTIVITY,"12001");
        var view=planner.planner(guest,trip.tripPublicId(),1);
        assertThat(view.items()).filteredOn(item->"12001".equals(item.contentId())).hasSize(1);
        assertThat(view.items().getFirst().slotType()).isEqualTo("DAY_FOCUS");
        String base="http://localhost:"+port+"/api/guests/"+guest+"/trips/"+trip.tripPublicId()+"/days/1";
        assertThat(request("POST",base+"/focus-recommendations",null).statusCode()).isEqualTo(200);
        assertThat(request("GET",base+"/focus-search?keyword=%EC%84%B1%EC%82%B0",null).statusCode()).isEqualTo(200);
        assertThat(request("DELETE",base+"/place-anchors/DAY_FOCUS",null).statusCode()).isEqualTo(204);
    }

    @Test void profileV2PersistsBloodSugarMappingAndMemberRestrictions() {
        var saved=profiles.create(guest,new FamilyProfileApplicationService.ProfileCommand("profile v2","CAR","PREFERRED","NORMAL","NO_PREFERENCE",false,
                List.of(new FamilyProfileApplicationService.MemberCommand("member",20,"NEUTRAL",List.of("NONE"),true,List.of("땅콩"),List.of("고수")))));
        var member=profiles.get(guest,saved.profileId()).members().getFirst();
        assertThat(member.bloodSugarCare()).isTrue();
        assertThat(member.mealCautions()).contains("SUGAR","CARBOHYDRATE").doesNotContain("NONE");
        assertThat(member.allergenRestrictions()).containsExactly("땅콩");
        assertThat(member.avoidedFoods()).containsExactly("고수");
    }
    @Test void plannerHydratesChronologicalItemsAndOnlyAdjacentTransit(){
        planner.select(guest,trip.tripPublicId(),1,TripPlannerView.SlotType.MORNING_ACTIVITY,"12001");
        planner.select(guest,trip.tripPublicId(),1,TripPlannerView.SlotType.AFTERNOON_ACTIVITY,"12002");
        planner.select(guest,trip.tripPublicId(),1,TripPlannerView.SlotType.STAY,"32001");
        var view=planner.planner(guest,trip.tripPublicId(),1);
        assertThat(view.items()).extracting(TripPlannerView.Item::slotType).containsExactly("BREAKFAST","MORNING_ACTIVITY","LUNCH","AFTERNOON_ACTIVITY","DINNER","STAY");
        assertThat(view.items()).allSatisfy(i->{assertThat(i.dataAvailability()).isEqualTo("CURRENT_DATA");assertThat(i.title()).startsWith("LIVE-");});
        assertThat(view.legs()).hasSize(5).allSatisfy(l->{assertThat(l.dataAvailability()).isEqualTo("CURRENT_DATA");assertThat(l.explicitWalkingDistanceMeters()).isEqualTo(500);assertThat(l.unaccountedDistanceMeters()).isEqualTo(900);});
        verify(transit,times(5)).findRoutes(any(),any());
        assertThat(columns("trip_day_place_anchors")).containsExactlyInAnyOrder("id","public_id","trip_day_id","slot_type","provider","content_id","content_type","created_at","updated_at");
    }
    @Test void liveAndTransitFailuresDegradeWithoutStaleFallback(){
        planner.select(guest,trip.tripPublicId(),1,TripPlannerView.SlotType.MORNING_ACTIVITY,"12001");
        doThrow(new IntegrationException("TOUR_API",IntegrationFailure.NETWORK_FAILURE)).when(source).fetchPlaceDetail("12001");
        doThrow(new IntegrationException("KAKAO",IntegrationFailure.NETWORK_FAILURE)).when(transit).findRoutes(any(),any());
        var view=planner.planner(guest,trip.tripPublicId(),1);
        assertThat(view.items().stream().filter(i->i.contentId().equals("12001")).findFirst().orElseThrow().dataAvailability()).isEqualTo("CURRENT_DATA_UNAVAILABLE");
        assertThat(view.items()).extracting(TripPlannerView.Item::contentId).doesNotContain("99999");
        assertThat(view.legs()).allSatisfy(l->assertThat(l.dataAvailability()).isEqualTo("UNAVAILABLE"));
    }
    @Test void mysqlConstraintsFlywayAndTripCascadeHold(){
        long before=count("trip_day_place_anchors");
        planner.select(guest,trip.tripPublicId(),1,TripPlannerView.SlotType.MORNING_ACTIVITY,"12001");
        long day=jdbc.queryForObject("SELECT id FROM trip_days WHERE trip_id=(SELECT id FROM trips WHERE public_id=?) AND day_number=1",Long.class,trip.tripPublicId());
        assertThatThrownBy(()->jdbc.update("INSERT INTO trip_day_place_anchors(public_id,trip_day_id,slot_type,provider,content_id,content_type) VALUES(?,?, 'MORNING_ACTIVITY','KTO','12002','12')",UUID.randomUUID().toString(),day)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("13");assertThat(flyway.info().pending()).isEmpty();assertThat(flyway.migrate().migrationsExecuted).isZero();
        trips.delete(guest,trip.tripPublicId());assertThat(count("trip_day_place_anchors")).isEqualTo(before);
    }
    @Test void httpResponsesAreNoStoreAndExposeNoPayloadPersistenceFields() throws Exception{
        String base="http://localhost:"+port+"/api/guests/"+guest+"/trips/"+trip.tripPublicId()+"/days/1";
        var recommended=request("POST",base+"/place-recommendations","{\"slotType\":\"MORNING_ACTIVITY\"}");
        assertThat(recommended.statusCode()).isEqualTo(200);assertThat(recommended.headers().firstValue("Cache-Control")).contains("no-store");
        var selected=request("PUT",base+"/place-anchors/MORNING_ACTIVITY","{\"contentId\":\"12001\",\"title\":\"untrusted\"}");
        assertThat(selected.statusCode()).isEqualTo(200);assertThat(selected.body()).doesNotContain("title","address","image");
        assertThat(request("GET",base+"/planner",null).statusCode()).isEqualTo(200);
        assertThat(request("DELETE",base+"/place-anchors/MORNING_ACTIVITY",null).statusCode()).isEqualTo(204);
    }
    HttpResponse<String> request(String method,String uri,String body)throws Exception{var b=HttpRequest.newBuilder(URI.create(uri)).header("Content-Type","application/json");return HttpClient.newHttpClient().send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
    TourismPlace place(String id,String type,TourismRegion region){return new TourismPlace(id,type,"LIST-"+id,"list address",null,null,null,new Coordinates(new BigDecimal("126.5"),new BigDecimal("33.5")),null,null,null,region.lDongRegnCd(),region.lDongSignguCd(),null,null,null,null,null,null);}
    TourismLivePlace live(String id){String type=id.startsWith("12")?"12":id.startsWith("32")?"32":"39";return new TourismLivePlace(id,type,"LIVE-"+id,"live address", "https://example.test/"+id+".jpg",new Coordinates(new BigDecimal("126.5").add(new BigDecimal(id.substring(id.length()-1)).movePointLeft(3)),new BigDecimal("33.5")),"50","110");}
    long seedStale(){jdbc.update("INSERT INTO tourism_places(content_id,content_type_id,title,l_dong_regn_cd,l_dong_signgu_cd) VALUES('99999','12','STALE CACHE','50','110')");long id=jdbc.queryForObject("SELECT id FROM tourism_places WHERE content_id='99999'",Long.class);jdbc.update("INSERT INTO restaurants(tourism_place_id) VALUES(?)",id);return id;}
    long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    List<String> columns(String table){return jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=?",String.class,table);}
}
