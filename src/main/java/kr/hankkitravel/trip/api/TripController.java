package kr.hankkitravel.trip.api;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import kr.hankkitravel.trip.application.*;
import kr.hankkitravel.trip.model.*;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.recommendation.api.RestaurantRecommendationController.RecommendationResponse;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/guests/{guestPublicId}/trips")
public class TripController {
    private final TripScheduleService schedules;
    private final TripDecisionService decisions;
    public TripController(TripScheduleService schedules,TripDecisionService decisions) { this.schedules=schedules;this.decisions=decisions; }
    @PostMapping
    public ResponseEntity<TripView> create(@PathVariable String guestPublicId,@RequestBody CreateRequest request) {
        return response(201,schedules.create(guestPublicId,new TripPlan(request.profileId(),request.regionKey(),request.startDate(),request.endDate(),request.days())));
    }
    @GetMapping
    public ResponseEntity<List<TripView.Summary>> list(@PathVariable String guestPublicId) { return response(200,schedules.list(guestPublicId)); }
    @GetMapping("/{tripPublicId}")
    public ResponseEntity<TripView> detail(@PathVariable String guestPublicId,@PathVariable String tripPublicId) {
        return response(200,schedules.detail(guestPublicId,tripPublicId));
    }
    @DeleteMapping("/{tripPublicId}")
    public ResponseEntity<Void> delete(@PathVariable String guestPublicId,@PathVariable String tripPublicId) {
        schedules.delete(guestPublicId,tripPublicId); return response(204,null);
    }
    @PostMapping("/{tripPublicId}/meal-slots/{mealSlotPublicId}/recommendations")
    public ResponseEntity<RecommendationResponse> recommend(@PathVariable String guestPublicId,@PathVariable String tripPublicId,@PathVariable String mealSlotPublicId) {
        return response(200,RecommendationResponse.from(decisions.recommend(guestPublicId,tripPublicId,mealSlotPublicId)));
    }
    @PutMapping("/{tripPublicId}/meal-slots/{mealSlotPublicId}/anchor")
    public ResponseEntity<TripView.Anchor> select(@PathVariable String guestPublicId,@PathVariable String tripPublicId,
            @PathVariable String mealSlotPublicId,@RequestBody AnchorRequest request) {
        return response(200,decisions.select(guestPublicId,tripPublicId,mealSlotPublicId,request.contentId()));
    }
    @DeleteMapping("/{tripPublicId}/meal-slots/{mealSlotPublicId}/anchor")
    public ResponseEntity<Void> clear(@PathVariable String guestPublicId,@PathVariable String tripPublicId,@PathVariable String mealSlotPublicId) {
        schedules.clear(guestPublicId,tripPublicId,mealSlotPublicId); return response(204,null);
    }
    @ExceptionHandler(TripProblem.class)
    ResponseEntity<ApiError> problem(TripProblem e) { return response(e.status(),new ApiError(e.code(),"여행 및 식사 슬롯 요청을 확인해 주세요.")); }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiError> missing() { return response(404,new ApiError("GUEST_OR_PROFILE_NOT_FOUND","게스트 또는 가족 프로필을 찾을 수 없습니다.")); }
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> invalid() { return response(400,new ApiError("INVALID_TRIP_REQUEST","입력값을 확인해 주세요.")); }
    @ExceptionHandler(IntegrationException.class)
    ResponseEntity<ApiError> unavailable() { return response(503,new ApiError("CURRENT_TOURISM_DATA_UNAVAILABLE","현재 관광정보를 불러오지 못했습니다.")); }
    private static <T> ResponseEntity<T> response(int status,T body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
    public record CreateRequest(long profileId,String regionKey,LocalDate startDate,LocalDate endDate,List<TripPlan.Day> days) { }
    public record AnchorRequest(String contentId) { }
    public record ApiError(String code,String message) { }
}
