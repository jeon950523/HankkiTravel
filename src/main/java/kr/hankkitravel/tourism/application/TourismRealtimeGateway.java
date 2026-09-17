package kr.hankkitravel.tourism.application;

import kr.hankkitravel.shared.geo.Coordinates;
import java.util.List;
import kr.hankkitravel.tourism.model.TourApiPage;
import kr.hankkitravel.tourism.model.TourismNutritionEvidence;
import kr.hankkitravel.tourism.model.TourismRegion;
import kr.hankkitravel.tourism.model.TourismRestaurantDetail;
import kr.hankkitravel.tourism.model.TourismRestaurantRuntimeData;
import kr.hankkitravel.tourism.model.TourismContentType;
import kr.hankkitravel.tourism.model.TourismLivePlace;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Composes current TourAPI data with local nutrition references without writing tourism payloads. */
@Service
public class TourismRealtimeGateway {
    private final TourismRealtimeSource source;
    private final TourismRestaurantRuntimeRules restaurantRules;
    private final TourismNutritionRuntimeMatcher nutrition;
    private final int maxPageSize;

    public TourismRealtimeGateway(TourismRealtimeSource source, TourismRestaurantRuntimeRules restaurantRules,
            TourismNutritionRuntimeMatcher nutrition, @Value("${hankki.tourism-live.max-page-size}") int maxPageSize) {
        this.source = source; this.restaurantRules = restaurantRules; this.nutrition = nutrition;
        if (maxPageSize < 1 || maxPageSize > 100) throw new IllegalArgumentException("실시간 목록 최대 건수 설정이 올바르지 않습니다.");
        this.maxPageSize = maxPageSize;
    }

    public TourApiPage restaurants(String regionKey, int page, int size) {
        if (page < 0 || size < 1 || size > maxPageSize) throw new IllegalArgumentException("페이지/건수 범위를 확인하세요.");
        return source.fetchRestaurantPage(region(regionKey), page + 1, size);
    }

    public TourApiPage restaurantsNear(Coordinates center, int radiusMeters, int page, int size) {
        if (center == null || radiusMeters < 1 || radiusMeters > 20000 || page < 0 || size < 1 || size > maxPageSize) {
            throw new IllegalArgumentException("반경 식당 조회 범위를 확인하세요.");
        }
        return source.fetchRestaurantNearby(center, radiusMeters, page + 1, size);
    }

    public DecisionData decisionData(String contentId) {
        if (contentId == null || !contentId.matches("[0-9]{1,20}")) throw new IllegalArgumentException("콘텐츠 ID 형식을 확인하세요.");
        TourismRestaurantDetail detail = source.fetchRestaurantDetail(contentId);
        TourismRestaurantRuntimeData restaurant = restaurantRules.evaluate(detail);
        var matches = restaurant.menuCandidates().stream().map(nutrition::match).toList();
        return new DecisionData(contentId, detail, restaurant, matches);
    }

    public TourApiPage places(TourismRegion region,TourismContentType type,int page,int size) {
        if(region==null||type==null||page<0||size<1||size>maxPageSize) throw new IllegalArgumentException("페이지/건수 범위를 확인하세요.");
        return source.fetchPlacePage(region,type,page+1,size);
    }

    public TourApiPage searchPlaces(TourismRegion region, TourismContentType type, String keyword, int page, int size) {
        if (region == null || type == null || keyword == null || keyword.isBlank() || keyword.trim().length() > 100
                || page < 0 || size < 1 || size > maxPageSize) {
            throw new IllegalArgumentException("검색어와 페이지 범위를 확인하세요.");
        }
        return source.searchPlacePage(region, type, keyword.trim(), page + 1, size);
    }

    public TourismLivePlace place(String contentId) {
        if(contentId==null||!contentId.matches("[0-9]{1,20}")) throw new IllegalArgumentException("콘텐츠 ID 형식을 확인하세요.");
        return source.fetchPlaceDetail(contentId);
    }

    public TourismRegion region(String regionKey) {
        if (regionKey == null) throw new IllegalArgumentException("지역을 지정하세요.");
        return switch (regionKey.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "jeju-city", "jejusi", "제주시" -> TourismRegion.JEJU_CITY;
            case "seogwipo", "서귀포시" -> TourismRegion.SEOGWIPO;
            case "gyeongju", "경주시" -> TourismRegion.GYEONGJU;
            default -> throw new IllegalArgumentException("지원하지 않는 지역입니다.");
        };
    }

    public record DecisionData(String contentId, TourismRestaurantDetail detail,
            TourismRestaurantRuntimeData restaurant, List<TourismNutritionEvidence> nutritionMatches) {
        public DecisionData { nutritionMatches = List.copyOf(nutritionMatches); }
    }
}
