package kr.hankkitravel.tourism.adapter;

import java.net.URI;
import java.util.Map;
import kr.hankkitravel.shared.integration.ExternalHttpClient;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import kr.hankkitravel.tourism.application.TourismRealtimeSource;
import kr.hankkitravel.tourism.model.TourApiPage;
import kr.hankkitravel.tourism.model.TourismRegion;
import kr.hankkitravel.tourism.model.TourismRestaurantDetail;
import org.springframework.web.util.UriComponentsBuilder;

/** User-facing live calls only. Responses are parsed in memory and never cached in a database. */
public final class TourApiRealtimeRestaurantClient implements TourismRealtimeSource {
    private final ExternalHttpClient http;
    private final TourApiPageParser pageParser;
    private final TourApiRestaurantDetailClient detailClient;
    private final TourApiRestaurantPresentationParser presentationParser;
    private final TourApiPlaceDetailParser placeDetailParser;
    private final String baseUrl;
    private final String serviceKey;

    public TourApiRealtimeRestaurantClient(ExternalHttpClient http, TourApiPageParser pageParser,
            TourApiRestaurantDetailClient detailClient, TourApiRestaurantPresentationParser presentationParser,
            TourApiPlaceDetailParser placeDetailParser,
            String baseUrl, String serviceKey) {
        this.http = http; this.pageParser = pageParser; this.detailClient = detailClient;
        this.presentationParser = presentationParser; this.placeDetailParser=placeDetailParser;
        this.baseUrl = baseUrl; this.serviceKey = serviceKey;
    }

    @Override
    public TourApiPage fetchRestaurantPage(TourismRegion region, int pageNo, int numOfRows) {
        if (region == null || pageNo < 1 || numOfRows < 1) throw new IllegalArgumentException("지역과 페이지 범위를 확인하세요.");
        return pageParser.parse(get("/areaBasedList2", Map.<String, Object>of("pageNo", pageNo, "numOfRows", numOfRows,
                "lDongRegnCd", region.lDongRegnCd(), "lDongSignguCd", region.lDongSignguCd())));
    }

    @Override
    public TourismRestaurantDetail fetchRestaurantDetail(String contentId) {
        validateContentId(contentId);
        var detail = detailClient.fetch(contentId);
        var presentation = presentationParser.parse(get("/detailCommon2", Map.<String, Object>of("contentId", contentId)));
        return detail.withPresentation(presentation);
    }

    @Override
    public TourApiPage fetchPlacePage(TourismRegion region, kr.hankkitravel.tourism.model.TourismContentType contentType,
            int pageNo,int numOfRows) {
        if(region==null||contentType==null||pageNo<1||numOfRows<1) throw new IllegalArgumentException("지역과 페이지 범위를 확인하세요.");
        return pageParser.parse(get("/areaBasedList2",Map.<String,Object>of("pageNo",pageNo,"numOfRows",numOfRows,
                "contentTypeId",contentType.code(),"lDongRegnCd",region.lDongRegnCd(),"lDongSignguCd",region.lDongSignguCd())));
    }

    @Override
    public TourApiPage searchPlacePage(TourismRegion region, kr.hankkitravel.tourism.model.TourismContentType contentType,
            String keyword, int pageNo, int numOfRows) {
        if (region == null || contentType == null || keyword == null || keyword.isBlank() || pageNo < 1 || numOfRows < 1) {
            throw new IllegalArgumentException("지역, 검색어와 페이지 범위를 확인하세요.");
        }
        return pageParser.parse(get("/searchKeyword2", Map.<String,Object>of("pageNo", pageNo, "numOfRows", numOfRows,
                "keyword", keyword.trim(), "contentTypeId", contentType.code(),
                "lDongRegnCd", region.lDongRegnCd(), "lDongSignguCd", region.lDongSignguCd())));
    }

    @Override
    public kr.hankkitravel.tourism.model.TourismLivePlace fetchPlaceDetail(String contentId) {
        validateContentId(contentId);
        return placeDetailParser.parse(get("/detailCommon2",Map.<String,Object>of("contentId",contentId)));
    }

    private String get(String endpoint, Map<String, Object> parameters) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IntegrationException("TOUR_API", IntegrationFailure.SECRET_NOT_PRESENT);
        }
        var builder = UriComponentsBuilder.fromUriString(baseUrl).path(endpoint)
                .queryParam("serviceKey", "{key}").queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "HankkiTravel").queryParam("_type", "json");
        parameters.forEach((name, value) -> builder.queryParam(name, value));
        URI uri = builder.encode().buildAndExpand(serviceKey).toUri();
        return http.get("TOUR_API", uri, Map.of());
    }

    private void validateContentId(String contentId) {
        if (contentId == null || !contentId.matches("[0-9]{1,20}")) {
            throw new IllegalArgumentException("콘텐츠 ID 형식을 확인하세요.");
        }
    }
}
