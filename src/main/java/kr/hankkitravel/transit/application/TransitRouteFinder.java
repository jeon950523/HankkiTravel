package kr.hankkitravel.transit.application;

import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.transit.model.TransitResult;

/** Normalized Kakao public-transit boundary for product use cases. */
public interface TransitRouteFinder {
    TransitResult findRoutes(Coordinates start, Coordinates end);
}
