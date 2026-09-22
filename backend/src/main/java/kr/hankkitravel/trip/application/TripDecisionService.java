package kr.hankkitravel.trip.application;

import java.util.List;
import kr.hankkitravel.recommendation.application.RestaurantRecommendationService;
import kr.hankkitravel.tourism.application.TourismRealtimeGateway;
import kr.hankkitravel.tourism.model.TourismContentType;
import kr.hankkitravel.tourism.model.TourismRegion;
import kr.hankkitravel.trip.model.TripProblem;
import org.springframework.stereotype.Service;

/** No transaction here: bounded live validation completes before the short write transaction. */
@Service
public class TripDecisionService {
    private final TripScheduleService schedules;
    private final RestaurantRecommendationService recommendations;
    private final TourismRealtimeGateway tourism;
    private final DayRecommendationOriginService contexts;
    public TripDecisionService(TripScheduleService schedules,RestaurantRecommendationService recommendations,
            TourismRealtimeGateway tourism,DayRecommendationOriginService contexts) {
        this.schedules=schedules; this.recommendations=recommendations; this.tourism=tourism; this.contexts=contexts;
    }
    public RestaurantRecommendationService.RecommendationResult recommend(String guest,String trip,String slot) {
        var c=schedules.context(guest,trip,slot);
        var context=contexts.resolve(guest,trip,c.dayNumber(),c.mealType());
        var anchor=context.origin();
        return recommendations.recommend(new RestaurantRecommendationService.RecommendationCommand(guest,c.profileId(),
                c.regionKey(),c.travelDate(),c.mealType(),anchor==null?"MEAL_FIRST":"PLACE_FIRST",anchor,null,List.of(),null));
    }
    public TripView.Anchor select(String guest,String trip,String slot,String contentId) {
        var c=schedules.context(guest,trip,slot);
        if(contentId==null || !contentId.matches("[0-9]{1,20}")) throw TripProblem.invalid("MEAL_ANCHOR_INVALID");
        var live=tourism.decisionData(contentId);
        var detail=live.detail();
        if(!contentId.equals(detail.contentId()) || !TourismContentType.RESTAURANT.code().equals(detail.contentType())
                || !List.of("MEAL","MIXED").contains(live.restaurant().classification()))
            throw TripProblem.invalid("MEAL_ANCHOR_INVALID");
        var expected="JEJU".equals(c.regionKey())?TourismRegion.JEJU_CITY:TourismRegion.GYEONGJU;
        if(different(detail.regionCode(),expected.lDongRegnCd())
                || ("GYEONGJU".equals(c.regionKey()) && different(detail.districtCode(),expected.lDongSignguCd())))
            throw TripProblem.invalid("MEAL_ANCHOR_REGION_MISMATCH");
        return schedules.select(guest,trip,slot,new TripScheduleService.ValidatedAnchor(contentId,detail.contentType()));
    }
    private boolean different(String actual,String expected) { return actual!=null && !actual.isBlank() && !expected.equals(actual); }
}
