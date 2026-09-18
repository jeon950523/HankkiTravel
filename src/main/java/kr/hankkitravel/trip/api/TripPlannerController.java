package kr.hankkitravel.trip.api;

import java.util.NoSuchElementException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.trip.application.*;
import kr.hankkitravel.trip.model.TripProblem;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/guests/{guestPublicId}/trips/{tripPublicId}/days/{dayNumber}")
public class TripPlannerController {
    private final TripPlannerService planner;
    public TripPlannerController(TripPlannerService planner){this.planner=planner;}
    @PostMapping("/place-recommendations")
    public ResponseEntity<TripPlannerView.Recommendations> places(@PathVariable String guestPublicId,@PathVariable String tripPublicId,
            @PathVariable int dayNumber,@RequestBody PlaceRecommendationRequest request){return response(200,planner.recommendActivities(guestPublicId,tripPublicId,dayNumber,request.slotType()));}
    @PostMapping("/focus-recommendations")
    public ResponseEntity<TripPlannerView.Recommendations> focus(@PathVariable String guestPublicId,@PathVariable String tripPublicId,
            @PathVariable int dayNumber){return response(200,planner.recommendFocus(guestPublicId,tripPublicId,dayNumber));}
    @GetMapping("/focus-search")
    public ResponseEntity<TripPlannerView.Recommendations> focusSearch(@PathVariable String guestPublicId,@PathVariable String tripPublicId,
            @PathVariable int dayNumber,@RequestParam String keyword){return response(200,planner.searchFocus(guestPublicId,tripPublicId,dayNumber,keyword));}
    @PostMapping("/stay-recommendations")
    public ResponseEntity<TripPlannerView.Recommendations> stays(@PathVariable String guestPublicId,@PathVariable String tripPublicId,@PathVariable int dayNumber){return response(200,planner.recommendStay(guestPublicId,tripPublicId,dayNumber));}
    @PostMapping("/place-alternatives")
    public ResponseEntity<TripPlannerView.Recommendations> placeAlternatives(@PathVariable String guestPublicId,@PathVariable String tripPublicId,
            @PathVariable int dayNumber,@RequestBody AlternativeRequest request){return response(200,planner.otherPlaces(guestPublicId,tripPublicId,dayNumber,request.slotType(),ids(request.exclude())));}
    @GetMapping("/place-search")
    public ResponseEntity<TripPlannerView.Recommendations> placeSearch(@PathVariable String guestPublicId,@PathVariable String tripPublicId,
            @PathVariable int dayNumber,@RequestParam TripPlannerView.SlotType slotType,@RequestParam String keyword){return response(200,planner.searchPlaces(guestPublicId,tripPublicId,dayNumber,slotType,keyword));}
    @PostMapping("/restaurant-alternatives")
    public ResponseEntity<TripPlannerView.Recommendations> restaurantAlternatives(@PathVariable String guestPublicId,@PathVariable String tripPublicId,
            @PathVariable int dayNumber,@RequestParam(required=false) String mealType,@RequestBody(required=false) ExcludeRequest request){return response(200,planner.otherRestaurants(guestPublicId,tripPublicId,dayNumber,mealType,ids(request==null?null:request.exclude())));}
    @GetMapping("/restaurant-search")
    public ResponseEntity<TripPlannerView.Recommendations> restaurantSearch(@PathVariable String guestPublicId,@PathVariable String tripPublicId,
            @PathVariable int dayNumber,@RequestParam(required=false) String mealType,@RequestParam String keyword){return response(200,planner.searchRestaurants(guestPublicId,tripPublicId,dayNumber,mealType,keyword));}
    @PostMapping("/dessert-recommendations")
    public ResponseEntity<TripPlannerView.Recommendations> desserts(@PathVariable String guestPublicId,@PathVariable String tripPublicId,@PathVariable int dayNumber,
            @RequestParam String mealType){return response(200,planner.recommendDesserts(guestPublicId,tripPublicId,dayNumber,mealType));}
    @PostMapping("/dessert-alternatives")
    public ResponseEntity<TripPlannerView.Recommendations> dessertAlternatives(@PathVariable String guestPublicId,@PathVariable String tripPublicId,@PathVariable int dayNumber,
            @RequestParam String mealType,@RequestBody(required=false) ExcludeRequest request){return response(200,planner.otherDesserts(guestPublicId,tripPublicId,dayNumber,mealType,ids(request==null?null:request.exclude())));}
    @GetMapping("/dessert-search")
    public ResponseEntity<TripPlannerView.Recommendations> dessertSearch(@PathVariable String guestPublicId,@PathVariable String tripPublicId,@PathVariable int dayNumber,
            @RequestParam String mealType,@RequestParam String keyword){return response(200,planner.searchDesserts(guestPublicId,tripPublicId,dayNumber,mealType,keyword));}
    @PutMapping("/place-anchors/{slotType}")
    public ResponseEntity<TripPlannerView.Reference> select(@PathVariable String guestPublicId,@PathVariable String tripPublicId,@PathVariable int dayNumber,
            @PathVariable TripPlannerView.SlotType slotType,@RequestBody AnchorRequest request){return response(200,planner.select(guestPublicId,tripPublicId,dayNumber,slotType,request.contentId()));}
    @DeleteMapping("/place-anchors/{slotType}")
    public ResponseEntity<Void> clear(@PathVariable String guestPublicId,@PathVariable String tripPublicId,@PathVariable int dayNumber,
            @PathVariable TripPlannerView.SlotType slotType){planner.clear(guestPublicId,tripPublicId,dayNumber,slotType);return response(204,null);}
    @GetMapping("/planner")
    public ResponseEntity<TripPlannerView.Planner> planner(@PathVariable String guestPublicId,@PathVariable String tripPublicId,@PathVariable int dayNumber){return response(200,planner.planner(guestPublicId,tripPublicId,dayNumber));}
    @ExceptionHandler(TripProblem.class) ResponseEntity<ApiError> problem(TripProblem e){return response(e.status(),new ApiError(e.code(),"하루 일정 요청을 확인해 주세요."));}
    @ExceptionHandler(NoSuchElementException.class) ResponseEntity<ApiError> missing(){return response(404,new ApiError("GUEST_OR_PROFILE_NOT_FOUND","게스트 또는 가족 프로필을 찾을 수 없습니다."));}
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalid(){return response(400,new ApiError("INVALID_PLANNER_REQUEST","플래너 입력값을 확인해 주세요."));}
    @ExceptionHandler(IntegrationException.class) ResponseEntity<ApiError> unavailable(){return response(503,new ApiError("CURRENT_TOURISM_DATA_UNAVAILABLE","현재 관광정보를 불러오지 못했습니다."));}
    private static <T> ResponseEntity<T> response(int status,T body){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);}
    private static Set<String> ids(String value){return value==null||value.isBlank()?Set.of():Arrays.stream(value.split(",")).map(String::trim)
            .filter(item->item.matches("[0-9]{1,20}")).collect(Collectors.toUnmodifiableSet());}
    public record PlaceRecommendationRequest(TripPlannerView.SlotType slotType){}
    public record AlternativeRequest(TripPlannerView.SlotType slotType,String exclude){}
    public record ExcludeRequest(String exclude){}
    public record AnchorRequest(String contentId){}
    public record ApiError(String code,String message){}
}
