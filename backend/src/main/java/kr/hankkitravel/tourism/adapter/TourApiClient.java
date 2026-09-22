package kr.hankkitravel.tourism.adapter;

import java.net.URI;
import java.util.List;
import java.util.Map;
import kr.hankkitravel.shared.integration.ExternalHttpClient;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import kr.hankkitravel.tourism.model.TourismPlace;
import org.springframework.web.util.UriComponentsBuilder;

public final class TourApiClient {
    private final ExternalHttpClient http;
    private final TourApiParser parser;
    private final String baseUrl;
    private final String serviceKey;

    public TourApiClient(ExternalHttpClient http, TourApiParser parser, String baseUrl, String serviceKey) {
        this.http = http;
        this.parser = parser;
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
    }

    /** Sync boundary only; never expose as a per-user search proxy. Uses the decoded portal key. */
    public List<TourismPlace> fetchPage(int page, int rows) {
        if (page < 1 || rows < 1) throw new IllegalArgumentException("페이지/건수 범위를 확인하세요.");
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IntegrationException("TOUR_API", IntegrationFailure.SECRET_NOT_PRESENT);
        }
        URI uri = UriComponentsBuilder.fromUriString(baseUrl).path("/areaBasedList2")
                .queryParam("serviceKey", "{key}").queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "HankkiTravel").queryParam("_type", "json")
                .queryParam("pageNo", page).queryParam("numOfRows", rows)
                .queryParam("contentTypeId", 12).queryParam("lDongRegnCd", 50)
                .encode().buildAndExpand(serviceKey).toUri();
        return parser.parse(http.get("TOUR_API", uri, Map.of()));
    }
}
