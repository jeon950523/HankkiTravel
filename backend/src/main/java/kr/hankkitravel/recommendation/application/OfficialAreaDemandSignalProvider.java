package kr.hankkitravel.recommendation.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import kr.hankkitravel.shared.integration.IntegrationException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
public final class OfficialAreaDemandSignalProvider implements AreaDemandSignalProvider {
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyyMM");
    private final TourismDemandStrengthSource strength;
    private final TourismResourceDemandSource resource;
    private final String configuredReferencePeriod;

    public OfficialAreaDemandSignalProvider(TourismDemandStrengthSource strength, TourismResourceDemandSource resource,
            @Value("${hankki.recommendation.demand-reference-period:}") String configuredReferencePeriod) {
        this.strength = strength;
        this.resource = resource;
        this.configuredReferencePeriod = configuredReferencePeriod == null ? "" : configuredReferencePeriod.trim();
    }

    @Override
    public Signal signal(RegionKey region, LocalDate date) {
        String period = configuredReferencePeriod.matches("[0-9]{6}") ? configuredReferencePeriod
                : YearMonth.from(date).minusMonths(1).format(PERIOD);
        Integer strengthScore = null;
        Integer resourceScore = null;
        try {
            var value = strength.fetch(region.areaCode(), region.districtCode(), period);
            strengthScore = average(normalize(value.stayIndex()), normalize(value.consumptionIndex()));
        } catch (IntegrationException ignored) { }
        try {
            var value = resource.fetch(region.areaCode(), region.districtCode(), period);
            resourceScore = average(normalize(value.tourismServiceIndex()), normalize(value.culturalResourceIndex()));
        } catch (IntegrationException ignored) { }
        if (strengthScore == null && resourceScore == null) return Signal.notEvaluated(period, 2, 2);
        int score = average(strengthScore, resourceScore);
        var sources = new ArrayList<String>();
        if (strengthScore != null) sources.add("한국관광공사 지역별 관광 수요 강도");
        if (resourceScore != null) sources.add("한국관광공사 지역별 관광 자원 수요");
        String reason = strengthScore != null && resourceScore != null
                ? "여행 시기의 체류·소비 강도와 관광·문화 자원 수요를 함께 참고했어요."
                : "확인 가능한 공식 지역 방문 수요 한 종류만 참고했어요.";
        return new Signal(true, score, period, reason, strengthScore != null, resourceScore != null,
                sources, 2, 2);
    }

    static Integer normalize(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) return null;
        return value.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private static Integer average(Integer left, Integer right) {
        if (left == null) return right;
        if (right == null) return left;
        return BigDecimal.valueOf(left + right).divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP).intValue();
    }
}
