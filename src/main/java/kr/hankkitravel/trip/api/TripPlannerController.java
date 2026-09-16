package kr.hankkitravel.trip.api;

import java.util.NoSuchElementException;
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
    @PostMapping("/stay-recommendations")
    public ResponseEntity<TripPlannerView.Recommendations> stays(@PathVariable String guestPublicId,@PathVariable String tripPublicId,@PathVariable int dayNumber){return response(200,planner.recommendStay(guestPublicId,tripPublicId,dayNumber));}
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
    public record PlaceRecommendationRequest(TripPlannerView.SlotType slotType){}
    public record AnchorRequest(String contentId){}
    public record ApiError(String code,String message){}
}
