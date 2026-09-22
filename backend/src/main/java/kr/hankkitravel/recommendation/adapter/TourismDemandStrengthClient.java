package kr.hankkitravel.recommendation.adapter;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import kr.hankkitravel.recommendation.application.TourismDemandStrengthSource;
import kr.hankkitravel.shared.integration.ExternalHttpClient;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import org.springframework.web.util.UriComponentsBuilder;

public final class TourismDemandStrengthClient implements TourismDemandStrengthSource {
    private final ExternalHttpClient http; private final OfficialDemandResponseParser parser = new OfficialDemandResponseParser();
    private final String baseUrl; private final String key;
    public TourismDemandStrengthClient(ExternalHttpClient http, String baseUrl, String key) {
        this.http = http; this.baseUrl = baseUrl; this.key = key;
    }
    @Override public Evidence fetch(String areaCode, String districtCode, String period) {
        BigDecimal stay = parser.normalizedIndex("KTO_DEMAND_STRENGTH", get("/areaTarSjrnDsList", areaCode, period, "tarSjrnDsIxCd", "21"), "tarSjrnDsIxVal", districtCode);
        BigDecimal consumption = parser.normalizedIndex("KTO_DEMAND_STRENGTH", get("/areaTarExpDsList", areaCode, period, "tarExpDsIxCd", "22"), "tarExpDsIxVal", districtCode);
        return new Evidence(stay, consumption);
    }
    private String get(String path, String area, String period, String indicatorName, String indicatorCode) {
        if (key == null || key.isBlank()) throw new IntegrationException("KTO_DEMAND_STRENGTH", IntegrationFailure.SECRET_NOT_PRESENT);
        URI uri = UriComponentsBuilder.fromUriString(baseUrl).path(path).queryParam("serviceKey", "{key}")
                .queryParam("MobileOS", "ETC").queryParam("MobileApp", "HankkiTravel").queryParam("_type", "json")
                .queryParam("numOfRows", 100).queryParam("pageNo", 1)
                .queryParam("baseYm", period).queryParam("areaCd", area).queryParam(indicatorName, indicatorCode)
                .encode().buildAndExpand(key).toUri();
        return http.get("KTO_DEMAND_STRENGTH", uri, Map.of());
    }
}
