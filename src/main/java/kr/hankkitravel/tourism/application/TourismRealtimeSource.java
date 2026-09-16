package kr.hankkitravel.tourism.application;

import kr.hankkitravel.tourism.model.TourApiPage;
import kr.hankkitravel.tourism.model.TourismRegion;
import kr.hankkitravel.tourism.model.TourismRestaurantDetail;
import kr.hankkitravel.tourism.model.TourismContentType;
import kr.hankkitravel.tourism.model.TourismLivePlace;

/** Live-only TourAPI boundary. Implementations must not write tourism payloads. */
public interface TourismRealtimeSource {
    TourApiPage fetchRestaurantPage(TourismRegion region, int pageNo, int numOfRows);
    TourismRestaurantDetail fetchRestaurantDetail(String contentId);
    TourApiPage fetchPlacePage(TourismRegion region, TourismContentType contentType, int pageNo, int numOfRows);
    TourApiPage searchPlacePage(TourismRegion region, TourismContentType contentType, String keyword, int pageNo, int numOfRows);
    TourismLivePlace fetchPlaceDetail(String contentId);
}
