package kr.hankkitravel.trip;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import kr.hankkitravel.identity.application.GuestApplicationService;
import kr.hankkitravel.profile.application.FamilyProfileApplicationService;
import kr.hankkitravel.recommendation.application.RestaurantRecommendationService;
import kr.hankkitravel.shared.integration.*;
import kr.hankkitravel.tourism.application.TourismRealtimeSource;
import kr.hankkitravel.tourism.model.*;
import kr.hankkitravel.trip.application.*;
import kr.hankkitravel.trip.model.*;
import kr.hankkitravel.trip.persistence.TripScheduleMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class TripMysqlIntegrationTest {
    @Container static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.4")
            .withDatabaseName("hankki_trip_test").withUsername("trip_test").withPassword(UUID.randomUUID().toString());
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl); r.add("spring.datasource.username",MYSQL::getUsername);
        r.add("spring.datasource.password",MYSQL::getPassword); r.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
    }
    @Autowired TripScheduleService schedules;
    @Autowired TripDecisionService decisions;
    @Autowired GuestApplicationService guests;
    @Autowired FamilyProfileApplicationService profiles;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    @MockitoBean TourismRealtimeSource source;
    @MockitoSpyBean TripScheduleMapper mapper;
    @MockitoSpyBean RestaurantRecommendationService recommendations;
    @LocalServerPort int port;
    final LocalDate start=LocalDate.of(2026,9,20);
    String guest;
    long profile;
    @BeforeEach void prepare() {
        guest=guests.create().getPublicId();
        profile=profiles.create(guest,profileCommand("PUBLIC_TRANSIT")).profileId();
        when(source.fetchRestaurantDetail(anyString())).thenAnswer(inv -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return detail(inv.getArgument(0),"39","50","110","비빔밥");
        });
        when(source.fetchRestaurantPage(any(),anyInt(),anyInt())).thenAnswer(inv -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new TourApiPage(List.of(new TourismPlace("91001","39","가상 테스트 식당",null,null,null,null,null,
                    null,null,null,"50","110",null,null,null,null,null,null)),1,16,1);
        });
    }
    FamilyProfileApplicationService.ProfileCommand profileCommand(String mode) {
        return new FamilyProfileApplicationService.ProfileCommand("test profile",mode,"NO_PREFERENCE","NORMAL","AVOID",false,
                List.of(new FamilyProfileApplicationService.MemberCommand("test member",30,"NEUTRAL",List.of("NONE"))));
    }
    TripPlan plan(int n) {
        return new TripPlan(profile,"JEJU",start,start.plusDays(n-1),java.util.stream.IntStream.rangeClosed(1,n)
                .mapToObj(i->new TripPlan.Day(i,List.of(TripPlan.MealType.DINNER,TripPlan.MealType.BREAKFAST,TripPlan.MealType.LUNCH))).toList());
    }
    String slot(TripView t) { return t.days().getFirst().mealSlots().getFirst().mealSlotPublicId(); }
    long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class); }
    @Test void createsAllDaysAndSlotsWithoutLiveCallsAndCascadesOnlySchedule() {
        long tripBefore=count("trips"),dayBefore=count("trip_days"),slotBefore=count("trip_meal_slots");
        var t=schedules.create(guest,plan(4));
        assertThat(t.durationDays()).isEqualTo(4);
        assertThat(t.days()).extracting(TripView.Day::travelDate).containsExactly(start,start.plusDays(1),start.plusDays(2),start.plusDays(3));
        assertThat(t.days().getFirst().mealSlots()).extracting(TripView.Slot::mealType).containsExactly("BREAKFAST","LUNCH","DINNER");
        assertThat(schedules.list(guest).getFirst().mealSlotCount()).isEqualTo(12);
        assertThat(schedules.detail(guest,t.tripPublicId())).isEqualTo(t);
        verifyNoInteractions(source);
        decisions.select(guest,t.tripPublicId(),slot(t),"91001");
        assertThat(schedules.list(guest).getFirst().selectedAnchorCount()).isEqualTo(1);
        clearInvocations(source);
        schedules.delete(guest,t.tripPublicId());
        assertThat(count("trips")).isEqualTo(tripBefore); assertThat(count("trip_days")).isEqualTo(dayBefore);
        assertThat(count("trip_meal_slots")).isEqualTo(slotBefore);
        assertThat(schedules.list(guest)).isEmpty(); assertThat(profiles.owned(guest,profile)).isNotNull();
        verifyNoInteractions(source);
    }
    @Test void ownershipAndParentChildTamperingReturn404WithoutLiveCalls() throws Exception {
        var a=schedules.create(guest,plan(1)); var b=schedules.create(guest,plan(1));
        String other=guests.create().getPublicId();
        assertThatThrownBy(()->schedules.create(other,plan(1))).isInstanceOf(NoSuchElementException.class);
        for(String method:List.of("GET","DELETE")) assertThat(request(method,other,a.tripPublicId(),null).statusCode()).isEqualTo(404);
        for(String suffix:List.of("/anchor","/recommendations")) {
            String method=suffix.equals("/anchor")?"PUT":"POST";
            String body=suffix.equals("/anchor")?"{\"contentId\":\"91001\"}":null;
            assertThat(request(method,guest,b.tripPublicId()+"/meal-slots/"+slot(a)+suffix,body).statusCode()).isEqualTo(404);
            assertThat(request(method,other,a.tripPublicId()+"/meal-slots/"+slot(a)+suffix,body).statusCode()).isEqualTo(404);
        }
        assertThat(request("GET",guest,"not-a-uuid",null).statusCode()).isEqualTo(404);
        assertThat(request("DELETE",guest,a.tripPublicId()+"/meal-slots/"+slot(b)+"/anchor",null).statusCode()).isEqualTo(404);
        verifyNoInteractions(source);
    }
    @Test void anchorValidationIdempotencyReplacementOutageAndClear() {
        var t=schedules.create(guest,plan(1)); long before=count("meal_anchors");
        var first=decisions.select(guest,t.tripPublicId(),slot(t),"91001");
        assertThat(decisions.select(guest,t.tripPublicId(),slot(t),"91001")).isEqualTo(first);
        assertThat(count("meal_anchors")).isEqualTo(before+1);
        assertThat(decisions.select(guest,t.tripPublicId(),slot(t),"91002").contentId()).isEqualTo("91002");
        when(source.fetchRestaurantDetail("91003")).thenThrow(new IntegrationException("TOUR_API",IntegrationFailure.UPSTREAM_REJECTED));
        assertThatThrownBy(()->decisions.select(guest,t.tripPublicId(),slot(t),"91003")).isInstanceOf(IntegrationException.class);
        assertThat(schedules.detail(guest,t.tripPublicId()).days().getFirst().mealSlots().getFirst().anchor().contentId()).isEqualTo("91002");
        for(var invalid:List.of(detail("91004","12","50","110","비빔밥"),detail("91004","39","47","130","비빔밥"),
                detail("91004","39","50","110","커피"),detail(null,null,null,null,null))) {
            when(source.fetchRestaurantDetail("91004")).thenReturn(invalid);
            assertThatThrownBy(()->decisions.select(guest,t.tripPublicId(),slot(t),"91004")).isInstanceOf(TripProblem.class);
        }
        assertThatThrownBy(()->decisions.select(guest,t.tripPublicId(),slot(t),"x'bad")).isInstanceOf(TripProblem.class);
        assertThat(count("meal_anchors")).isEqualTo(before+1);
        schedules.clear(guest,t.tripPublicId(),slot(t)); schedules.clear(guest,t.tripPublicId(),slot(t));
        assertThat(count("meal_anchors")).isEqualTo(before);
    }
    @Test void recommendationUsesCurrentProfileStoredDayMealAndExistingMovementDefense() {
        var t=schedules.create(guest,plan(2)); var s=t.days().get(1).mealSlots().get(2);
        var result=decisions.recommend(guest,t.tripPublicId(),s.mealSlotPublicId());
        assertThat(result.context().tripDate()).isEqualTo(start.plusDays(1));
        assertThat(result.context().mealType()).isEqualTo("DINNER");
        assertThat(result.context().region()).isEqualTo("jeju-city");
        assertThat(result.perspectives()).filteredOn(p->p.perspective().equals("MOBILITY_PRIORITY"))
                .extracting(RestaurantRecommendationService.PerspectiveResult::status).containsExactly("MOVEMENT_CONTEXT_REQUIRED");
        verify(recommendations).recommend(argThat(c->c.profileId()==profile && c.guestPublicId().equals(guest) && c.anchor()==null));
        profiles.update(guest,profile,profileCommand("CAR"));
        assertThat(decisions.recommend(guest,t.tripPublicId(),s.mealSlotPublicId()).perspectives()).allMatch(p->p.status().equals("READY"));
        assertThat(schedules.list(guest).getFirst().selectedAnchorCount()).isZero();
        assertThat(count("tourism_places")).isZero(); assertThat(count("restaurants")).isZero();
        var g=schedules.create(guest,new TripPlan(profile,"GYEONGJU",start,start,List.of(new TripPlan.Day(1,List.of(TripPlan.MealType.LUNCH)))));
        decisions.recommend(guest,g.tripPublicId(),slot(g));
        verify(source).fetchRestaurantPage(eq(TourismRegion.GYEONGJU),eq(1),eq(16));
    }
    @Test void createRollbackIsAtomicWhenSlotInsertFails() {
        long trips=count("trips"),days=count("trip_days"),slots=count("trip_meal_slots");
        AtomicInteger calls=new AtomicInteger();
        var actualMapper=sqlSession.getMapper(TripScheduleMapper.class);
        doAnswer(inv->{if(calls.incrementAndGet()==2) throw new IllegalStateException("injected write failure");return actualMapper.insertSlot(inv.getArgument(0));})
                .when(mapper).insertSlot(any());
        assertThatThrownBy(()->schedules.create(guest,plan(2))).isInstanceOf(IllegalStateException.class);
        assertThat(count("trips")).isEqualTo(trips); assertThat(count("trip_days")).isEqualTo(days); assertThat(count("trip_meal_slots")).isEqualTo(slots);
    }
    @Test void mysqlUniquenessAndReferenceOnlyColumns() {
        var t=schedules.create(guest,plan(1));
        long tid=jdbc.queryForObject("SELECT id FROM trips WHERE public_id=?",Long.class,t.tripPublicId());
        long did=jdbc.queryForObject("SELECT id FROM trip_days WHERE trip_id=?",Long.class,tid);
        long sid=jdbc.queryForObject("SELECT id FROM trip_meal_slots WHERE public_id=?",Long.class,slot(t));
        assertThatThrownBy(()->jdbc.update("INSERT INTO trip_days(trip_id,day_number,travel_date) VALUES(?,?,?)",tid,1,start.plusDays(1))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO trip_days(trip_id,day_number,travel_date) VALUES(?,?,?)",tid,2,start)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO trip_meal_slots(public_id,trip_day_id,meal_type) VALUES(?,?,?)",UUID.randomUUID().toString(),did,"LUNCH")).isInstanceOf(DataIntegrityViolationException.class);
        decisions.select(guest,t.tripPublicId(),slot(t),"91001");
        assertThatThrownBy(()->jdbc.update("INSERT INTO meal_anchors(trip_meal_slot_id,provider,content_id,content_type) VALUES(?,'KTO','91002','39')",sid)).isInstanceOf(DataIntegrityViolationException.class);
        var cols=jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='meal_anchors'",String.class);
        assertThat(cols).containsExactlyInAnyOrder("id","trip_meal_slot_id","provider","content_id","content_type","created_at","updated_at");
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("15");
        assertThat(flyway.migrate().migrationsExecuted).isZero(); assertThat(flyway.info().pending()).isEmpty();
    }
    @Test void concurrentAnchorPutsKeepExactlyOneReference() throws Exception {
        var t=schedules.create(guest,plan(1)); long before=count("meal_anchors");
        try(var executor=Executors.newFixedThreadPool(2)) {
            var tasks=List.<Callable<TripView.Anchor>>of(()->decisions.select(guest,t.tripPublicId(),slot(t),"91001"),
                    ()->decisions.select(guest,t.tripPublicId(),slot(t),"91002"));
            for(var future:executor.invokeAll(tasks)) assertThat(future.get().provider()).isEqualTo("KTO");
        }
        assertThat(count("meal_anchors")).isEqualTo(before+1);
    }
    @Test void httpGoldenFlowNoStoreCorsAndSafeErrorContract() throws Exception {
        String payload="""
            {"profileId":%d,"regionKey":"JEJU","startDate":"2026-09-20","endDate":"2026-09-21",
             "days":[{"dayNumber":1,"mealTypes":[]},{"dayNumber":2,"mealTypes":["LUNCH"]}]}
            """.formatted(profile);
        var created=request("POST",guest,"",payload); assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("Cache-Control")).contains("no-store");
        var parsed=JsonMapper.builder().build().readTree(created.body()); String tid=parsed.get("tripPublicId").asText();
        String sid=parsed.get("days").get(1).get("mealSlots").get(0).get("mealSlotPublicId").asText();
        String path=tid+"/meal-slots/"+sid;
        assertThat(request("GET",guest,"",null).statusCode()).isEqualTo(200);
        assertThat(request("GET",guest,tid,null).body()).doesNotContain("tripDayId","guestId");
        var recommended=request("POST",guest,path+"/recommendations",null); assertThat(recommended.statusCode()).isEqualTo(200);
        assertThat(recommended.body()).contains("compatibilityScore","MOVEMENT_CONTEXT_REQUIRED");
        var selected=request("PUT",guest,path+"/anchor","{\"contentId\":\"91001\",\"title\":\"untrusted\"}");
        assertThat(selected.statusCode()).isEqualTo(200); assertThat(selected.body()).doesNotContain("title","address","score","untrusted");
        assertThat(request("DELETE",guest,path+"/anchor",null).statusCode()).isEqualTo(204);
        assertThat(request("DELETE",guest,path+"/anchor",null).statusCode()).isEqualTo(204);
        var preflight=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/guests/"+guest+"/trips/"+tid))
                .header("Origin","http://localhost:5175").header("Access-Control-Request-Method","DELETE")
                .method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(preflight.statusCode()).isEqualTo(200);
        assertThat(request("DELETE",guest,tid,null).statusCode()).isEqualTo(204);
        assertThat(request("GET",guest,tid,null).statusCode()).isEqualTo(404);
        var invalid=request("POST",guest,"",payload.replace("LUNCH","SNACK")); assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.headers().firstValue("Cache-Control")).contains("no-store");
    }
    HttpResponse<String> request(String method,String owner,String tail,String body) throws Exception {
        var req=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/guests/"+owner+"/trips"+(tail.isEmpty()?"":"/"+tail)))
                .header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(req.build(),HttpResponse.BodyHandlers.ofString());
    }
    TourismRestaurantDetail detail(String id,String type,String region,String district,String menu) {
        return new TourismRestaurantDetail(menu,menu,null,null,null,"가상 테스트 식당","가상 주소",null,id,type,region,district);
    }
}
