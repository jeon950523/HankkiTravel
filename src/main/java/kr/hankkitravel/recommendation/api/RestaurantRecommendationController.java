package kr.hankkitravel.recommendation.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import kr.hankkitravel.recommendation.application.RecommendationCore;
import kr.hankkitravel.recommendation.application.RestaurantRecommendationService;
import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.shared.integration.IntegrationException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations/restaurants")
public class RestaurantRecommendationController {
    private final RestaurantRecommendationService recommendations;

    public RestaurantRecommendationController(RestaurantRecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @PostMapping
    public ResponseEntity<RecommendationResponse> recommend(@RequestBody RecommendationRequest request) {
        var result = recommendations.recommend(request.command());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(RecommendationResponse.from(result));
    }

    @ExceptionHandler(IntegrationException.class)
    ResponseEntity<ApiError> tourismUnavailable() {
        return noStore(HttpStatus.SERVICE_UNAVAILABLE, new ApiError("CURRENT_TOURISM_DATA_UNAVAILABLE",
                "현재 관광정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiError> profileMissing() {
        return noStore(HttpStatus.NOT_FOUND, new ApiError("PROFILE_NOT_FOUND", "요청한 프로필을 찾을 수 없습니다."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalid() {
        return noStore(HttpStatus.BAD_REQUEST, new ApiError("INVALID_RECOMMENDATION_REQUEST", "추천 조건을 확인해 주세요."));
    }

    private <T> ResponseEntity<T> noStore(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }

    public record RecommendationRequest(String guestPublicId, long profileId, String region, LocalDate tripDate,
            String mealType, String startMode, AnchorRequest anchor, String desiredLocalFood, List<String> strictExclusions,
            BigDecimal maximumTransitMinutes) {
        RestaurantRecommendationService.RecommendationCommand command() {
            return new RestaurantRecommendationService.RecommendationCommand(guestPublicId, profileId, region, tripDate,
                    mealType, startMode, anchor == null ? null : anchor.coordinates(), desiredLocalFood, strictExclusions,
                    maximumTransitMinutes);
        }
    }
    public record AnchorRequest(BigDecimal longitude, BigDecimal latitude) {
        Coordinates coordinates() { return new Coordinates(longitude, latitude); }
    }
    public record RecommendationResponse(Context context, List<PerspectiveResponse> perspectives,
            List<RestaurantResponse> topCandidates, int candidateCount,
            CallSummary callSummary, String dataAvailability, String sourceAttribution, String nutritionNotice) {
        public static RecommendationResponse from(RestaurantRecommendationService.RecommendationResult result) {
            return new RecommendationResponse(new Context(result.context().region(), result.context().tripDate(),
                    result.context().mealType(), result.context().startMode()), result.perspectives().stream()
                    .map(PerspectiveResponse::from).toList(),result.topCandidates().stream().map(RestaurantResponse::from).toList(), result.candidateCount(),
                    new CallSummary(result.callSummary().tourListCalls(), result.callSummary().tourDetailCalls(),
                            result.callSummary().kakaoTransitCalls(), result.callSummary().elapsedMillis()),
                    result.dataAvailability(), result.sourceAttribution(), result.nutritionNotice());
        }
    }
    public record Context(String region, LocalDate tripDate, String mealType, String startMode) { }
    public record CallSummary(int tourListCalls, int tourDetailCalls, int kakaoTransitCalls, long elapsedMillis) { }
    public record PerspectiveResponse(String perspective, String status, String message, List<RestaurantResponse> candidates) {
        static PerspectiveResponse from(RestaurantRecommendationService.PerspectiveResult result) {
            return new PerspectiveResponse(result.perspective(), result.status(), result.message(),
                    result.candidates().stream().map(RestaurantResponse::from).toList());
        }
    }
    public record RestaurantResponse(String contentId, String title, String areaLabel, String address,
            kr.hankkitravel.shared.geo.Coordinates coordinates, String imageUrl, int compatibilityScore, int overallScore,
            int evaluatedWeight, int evidenceCoverage, String informationEvidence, String phone, String placeUrl,
            String contactEvidence, List<String> ourFamilyFitReasons,
            List<String> ourFamilyCautions, List<String> checkBeforeVisit, List<NutritionResponse> nutritionEvidence,
            TransportResponse transportEvidence, List<DimensionResponse> evaluatedDimensions) {
        static RestaurantResponse from(RecommendationCore.RankedCandidate item) {
            var candidate = item.candidate();
            var restaurant = candidate.candidate();
            var transit = restaurant.transit();
            return new RestaurantResponse(restaurant.contentId(), restaurant.title(), restaurant.areaLabel(),restaurant.address(),
                    restaurant.coordinates(),restaurant.imageUrl(), item.compatibilityScore(),item.compatibilityScore(),
                    item.evaluatedWeight(),item.evidenceCoverage(),candidate.informationEvidence().name(),restaurant.phone(),
                    restaurant.placeUrl(),restaurant.contactEvidence(),
                    candidate.positives(), candidate.cautions(), candidate.checks(), restaurant.menus().stream()
                    .filter(MenuEvidenceView::displayable).map(MenuEvidenceView::from).toList(), transit == null ? null
                    : new TransportResponse(transit.totalTimeMinutes(), transit.transferCount(), transit.explicitWalkingDistanceMeters(),
                            transit.unaccountedDistanceMeters() > 0, transit.landingUrl()), candidate.dimensions().entrySet().stream()
                    .map(entry -> new DimensionResponse(entry.getKey(),label(entry.getKey()),entry.getValue().weight(),
                            entry.getValue().evaluated(),entry.getValue().awardedPoints(),entry.getValue().maxPoints(),
                            entry.getValue().state().name(),entry.getValue().reasons(),entry.getValue().score()))
                    .toList());
        }
        private static String label(String code){return switch(code){case RecommendationCore.FAMILY_MEAL_FIT->"식사 조건";case RecommendationCore.TRIP_ROUTE_FIT->"여행 동선";case RecommendationCore.MOBILITY_FIT->"이동 편의";case RecommendationCore.AREA_DEMAND_SIGNAL->"지역 방문 수요";case RecommendationCore.REVIEW_SIGNAL->"후기/평판";default->"지역/메뉴";};}
    }
    public record NutritionResponse(String menuName, String matchLevel, String standardFood, String referenceLabel) { }
    private record MenuEvidenceView(String rawName, String matchLevel, String standardFood) {
        static boolean displayable(RecommendationCore.MenuEvidence value) {
            return value.standardFood() != null && ("HIGH".equals(value.matchLevel()) || "MEDIUM".equals(value.matchLevel()));
        }
        static NutritionResponse from(RecommendationCore.MenuEvidence value) {
            return new NutritionResponse(value.rawName(), value.matchLevel(), value.standardFood(),
                    "HIGH".equals(value.matchLevel()) ? "표준 음식 기준" : "유사 음식 기준");
        }
    }
    public record TransportResponse(BigDecimal totalTimeMinutes, int transferCount, long explicitWalkingDistanceMeters,
            boolean walkingDetailCheckRequired, String kakaoMapLandingUrl) { }
    public record DimensionResponse(String code,String label,int weight,boolean evaluated,int awardedPoints,
            int maxPoints,String evidenceState,List<String> reasons,int score) { }
    public record ApiError(String code, String message) { }
}
