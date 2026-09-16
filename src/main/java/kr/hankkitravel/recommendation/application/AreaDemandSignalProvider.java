package kr.hankkitravel.recommendation.application;

import java.time.LocalDate;
import java.util.List;

/** Request-scoped official regional-demand boundary. Missing evidence never becomes a zero. */
public interface AreaDemandSignalProvider {
    Signal signal(RegionKey region, LocalDate date);

    record RegionKey(String areaCode, String districtCode, String label) {
        public RegionKey {
            if (areaCode == null || areaCode.isBlank() || districtCode == null || districtCode.isBlank()) {
                throw new IllegalArgumentException("지역 수요 조회 코드가 필요합니다.");
            }
        }
        public String cacheKey() { return areaCode + ":" + districtCode; }
    }

    record Signal(boolean evaluated, int score, String referencePeriod, String reason,
            boolean demandStrengthEvaluated, boolean resourceDemandEvaluated,
            List<String> sourceAttributions, int demandStrengthCalls, int resourceDemandCalls) {
        public Signal { sourceAttributions = List.copyOf(sourceAttributions); }
        public static Signal notEvaluated(String period, int strengthCalls, int resourceCalls) {
            return new Signal(false, 0, period, "공식 지역 방문 수요를 확인하지 못해 평가하지 않았어요.",
                    false, false, List.of(), strengthCalls, resourceCalls);
        }
    }
}
