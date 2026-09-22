package kr.hankkitravel.tourism.adapter;

import java.net.URI;
import java.util.Map;
import kr.hankkitravel.shared.integration.ExternalHttpClient;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import kr.hankkitravel.tourism.application.TourismSnapshotSource;
import kr.hankkitravel.tourism.model.TourApiPage;
import kr.hankkitravel.tourism.model.TourismSyncScope;
import org.springframework.web.util.UriComponentsBuilder;

/** Full legal-dong snapshot source. It is intentionally not a user-facing query adapter. */
public final class TourApiSnapshotClient implements TourismSnapshotSource {

    private final ExternalHttpClient http;
    private final TourApiPageParser parser;
    private final String baseUrl;
    private final String serviceKey;

    public TourApiSnapshotClient(ExternalHttpClient http, TourApiPageParser parser,
            String baseUrl, String serviceKey) {
        this.http = http;
        this.parser = parser;
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
    }

    @Override
    public TourApiPage fetch(TourismSyncScope scope, int pageNo, int numOfRows) {
        if (scope == null || pageNo < 1 || numOfRows < 1) {
            throw new IllegalArgumentException("페이지/건수 범위를 확인하세요.");
        }
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IntegrationException("TOUR_API", IntegrationFailure.SECRET_NOT_PRESENT);
        }
        URI uri = UriComponentsBuilder.fromUriString(baseUrl).path("/areaBasedList2")
                .queryParam("serviceKey", "{key}").queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "HankkiTravel").queryParam("_type", "json")
                .queryParam("pageNo", pageNo).queryParam("numOfRows", numOfRows).queryParam("arrange", "C")
                .queryParam("contentTypeId", scope.contentTypeId()).queryParam("lDongRegnCd", scope.lDongRegnCd())
                .queryParam("lDongSignguCd", scope.lDongSignguCd())
                .encode().buildAndExpand(serviceKey).toUri();
        return parser.parse(http.get("TOUR_API", uri, Map.of()));
    }
}
