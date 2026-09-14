package kr.hankkitravel.tourism.application;

import kr.hankkitravel.tourism.model.TourApiPage;
import kr.hankkitravel.tourism.model.TourismRegion;
import kr.hankkitravel.tourism.model.TourismRestaurantDetail;

/** Live-only TourAPI boundary. Implementations must not write tourism payloads. */
public interface TourismRealtimeSource {
    TourApiPage fetchRestaurantPage(TourismRegion region, int pageNo, int numOfRows);
    TourismRestaurantDetail fetchRestaurantDetail(String contentId);
}
