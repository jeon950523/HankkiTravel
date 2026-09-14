package kr.hankkitravel.tourism.adapter;

import java.net.URI;
import java.util.Map;
import kr.hankkitravel.shared.integration.ExternalHttpClient;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import kr.hankkitravel.tourism.model.TourismRestaurantDetail;
import org.springframework.web.util.UriComponentsBuilder;

/** TourAPI detailIntro2 exposes restaurant-only fields for content type 39. */
public final class TourApiRestaurantDetailClient {
    private final ExternalHttpClient http;
    private final TourApiRestaurantDetailParser parser;
    private final String baseUrl;
    private final String serviceKey;

    public TourApiRestaurantDetailClient(ExternalHttpClient http, TourApiRestaurantDetailParser parser,
            String baseUrl, String serviceKey) {
        this.http = http; this.parser = parser; this.baseUrl = baseUrl; this.serviceKey = serviceKey;
    }

    public TourismRestaurantDetail fetch(String contentId) {
        if (contentId == null || !contentId.matches("[0-9]{1,20}")) {
            throw new IllegalArgumentException("콘텐츠 ID 형식을 확인하세요.");
        }
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IntegrationException("TOUR_API", IntegrationFailure.SECRET_NOT_PRESENT);
        }
        URI uri = UriComponentsBuilder.fromUriString(baseUrl).path("/detailIntro2")
                .queryParam("serviceKey", "{key}").queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "HankkiTravel").queryParam("_type", "json")
                .queryParam("contentId", contentId).queryParam("contentTypeId", "39")
                .encode().buildAndExpand(serviceKey).toUri();
        return parser.parse(http.get("TOUR_API", uri, Map.of()));
    }
}
