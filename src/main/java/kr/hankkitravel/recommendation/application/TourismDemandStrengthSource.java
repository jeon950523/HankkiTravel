package kr.hankkitravel.recommendation.application;

import java.math.BigDecimal;

public interface TourismDemandStrengthSource {
    Evidence fetch(String areaCode, String districtCode, String referencePeriod);
    record Evidence(BigDecimal stayIndex, BigDecimal consumptionIndex) { }
}
