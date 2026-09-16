package kr.hankkitravel.recommendation.adapter;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import kr.hankkitravel.recommendation.application.TourismResourceDemandSource;
import kr.hankkitravel.shared.integration.ExternalHttpClient;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import org.springframework.web.util.UriComponentsBuilder;

public final class TourismResourceDemandClient implements TourismResourceDemandSource {
    private final ExternalHttpClient http; private final OfficialDemandResponseParser parser = new OfficialDemandResponseParser();
    private final String baseUrl; private final String key;
    public TourismResourceDemandClient(ExternalHttpClient http, String baseUrl, String key) {
        this.http = http; this.baseUrl = baseUrl; this.key = key;
    }
    @Override public Evidence fetch(String areaCode, String districtCode, String period) {
        BigDecimal service = parser.normalizedIndex("KTO_RESOURCE_DEMAND", get("/areaTarSvcDemList", areaCode, period, "tarSvcDemIxCd", "11"), "tarSvcDemIxVal", districtCode);
        BigDecimal culture = parser.normalizedIndex("KTO_RESOURCE_DEMAND", get("/areaCulResDemList", areaCode, period, "culResDemIxCd", "12"), "culResDemIxVal", districtCode);
        return new Evidence(service, culture);
    }
    private String get(String path, String area, String period, String indicatorName, String indicatorCode) {
        if (key == null || key.isBlank()) throw new IntegrationException("KTO_RESOURCE_DEMAND", IntegrationFailure.SECRET_NOT_PRESENT);
        URI uri = UriComponentsBuilder.fromUriString(baseUrl).path(path).queryParam("serviceKey", "{key}")
                .queryParam("MobileOS", "ETC").queryParam("MobileApp", "HankkiTravel").queryParam("_type", "json")
                .queryParam("numOfRows", 100).queryParam("pageNo", 1)
                .queryParam("baseYm", period).queryParam("areaCd", area).queryParam(indicatorName, indicatorCode)
                .encode().buildAndExpand(key).toUri();
        return http.get("KTO_RESOURCE_DEMAND", uri, Map.of());
    }
}
