package kr.hankkitravel.recommendation.application;

import java.math.BigDecimal;

public interface TourismResourceDemandSource {
    Evidence fetch(String areaCode, String districtCode, String referencePeriod);
    record Evidence(BigDecimal tourismServiceIndex, BigDecimal culturalResourceIndex) { }
}
