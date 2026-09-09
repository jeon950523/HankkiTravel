package kr.hankkitravel.tourism.application;

import kr.hankkitravel.tourism.model.TourApiPage;
import kr.hankkitravel.tourism.model.TourismSyncScope;

public interface TourismSnapshotSource {
    TourApiPage fetch(TourismSyncScope scope, int pageNo, int numOfRows);
}
