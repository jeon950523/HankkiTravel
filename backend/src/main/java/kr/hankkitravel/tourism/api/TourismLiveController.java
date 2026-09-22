package kr.hankkitravel.tourism.api;

import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import kr.hankkitravel.tourism.application.TourismRealtimeGateway;
import kr.hankkitravel.tourism.model.TourismNutritionEvidence;
import kr.hankkitravel.tourism.model.TourismRegion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Live TourAPI response boundary. Every response is explicitly excluded from browser and proxy storage. */
@RestController
@RequestMapping("/api/tourism/live")
public class TourismLiveController {
    private static final Logger log = LoggerFactory.getLogger(TourismLiveController.class);
    private static final String ATTRIBUTION = "출처: ⓒ한국관광공사";
    private static final String NOTICE = "공공 식품영양DB의 표준 음식 참고정보이며, 특정 매장의 실제 제공량·레시피·섭취량이 아닙니다.";
    private final TourismRealtimeGateway gateway;

    public TourismLiveController(TourismRealtimeGateway gateway) { this.gateway = gateway; }

    @GetMapping("/restaurants")
    public ResponseEntity<TourismLiveResponse.RestaurantPage> restaurants(@RequestParam String region,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        TourismRegion parsedRegion = gateway.region(region);
        var result = gateway.restaurants(region, page, size);
        var items = result.items().stream().map(place -> new TourismLiveResponse.Restaurant(place.contentId(),
                place.title(), place.address(), place.tel(), place.firstImage())).toList();
        return noStore(HttpStatus.OK, new TourismLiveResponse.RestaurantPage(regionName(parsedRegion), items,
                page, size, result.totalCount(), ATTRIBUTION));
    }

    @GetMapping("/restaurants/{contentId}/decision-data")
    public ResponseEntity<TourismLiveResponse.DecisionData> decisionData(
            @org.springframework.web.bind.annotation.PathVariable String contentId) {
        var result = gateway.decisionData(contentId);
        var detail = result.detail();
        var menus = result.nutritionMatches().stream().map(this::menu).toList();
        return noStore(HttpStatus.OK, new TourismLiveResponse.DecisionData(result.contentId(), detail.title(),
                detail.address(), detail.firstImage(), detail.firstMenu(), detail.treatMenu(), detail.openTime(),
                detail.restDate(), detail.parking(), new TourismLiveResponse.Classification(
                        result.restaurant().classification(), result.restaurant().reason()), menus, NOTICE, ATTRIBUTION));
    }

    @ExceptionHandler(IntegrationException.class)
    public ResponseEntity<TourismLiveResponse.Error> upstream(IntegrationException exception) {
        log.warn("Live TourAPI request failed: {}", exception.failure());
        HttpStatus status = exception.failure() == IntegrationFailure.SECRET_NOT_PRESENT
                || exception.failure() == IntegrationFailure.QUOTA_EXCEEDED ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        return noStore(status, new TourismLiveResponse.Error("LIVE_TOURISM_UNAVAILABLE",
                "실시간 관광 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<TourismLiveResponse.Error> invalidRequest(IllegalArgumentException exception) {
        return noStore(HttpStatus.BAD_REQUEST, new TourismLiveResponse.Error("INVALID_LIVE_TOURISM_REQUEST",
                "요청한 지역 또는 콘텐츠 ID를 확인해 주세요."));
    }

    private TourismLiveResponse.MenuEvidence menu(TourismNutritionEvidence evidence) {
        var reference = evidence.referenceNutrition();
        TourismLiveResponse.ReferenceNutrition responseReference = reference == null ? null
                : new TourismLiveResponse.ReferenceNutrition(reference.referenceBasis(), reference.referenceAmount(),
                        reference.referenceUnit(), reference.energyKcal(), reference.carbohydrateG(), reference.sugarG(),
                        reference.proteinG(), reference.fatG(), reference.sodiumMg(),
                        new TourismLiveResponse.Provenance(reference.provenance().sourceDatasetName(),
                                reference.provenance().sourceDatasetVersion(), reference.provenance().sourceInstitution()));
        return new TourismLiveResponse.MenuEvidence(evidence.rawMenuName(), evidence.normalizedMenuName(),
                evidence.sourceField(), evidence.matchLevel(), evidence.matchMethod(), evidence.reviewState(),
                evidence.evidence(), evidence.ruleVersion(), evidence.matchedStandardFood(), responseReference);
    }

    private String regionName(TourismRegion region) {
        return switch (region) { case JEJU_CITY -> "제주시"; case SEOGWIPO -> "서귀포시"; case GYEONGJU -> "경주시"; };
    }

    private <T> ResponseEntity<T> noStore(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
