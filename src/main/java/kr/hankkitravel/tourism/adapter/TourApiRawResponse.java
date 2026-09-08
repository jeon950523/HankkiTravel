package kr.hankkitravel.tourism.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
record TourApiRawResponse(Response response) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(Header header, Body body) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Header(String resultCode) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Body(JsonNode items, Integer totalCount) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String contentid, String contenttypeid, String title, String addr1,
            String mapx, String mapy, String modifiedtime) {}
}
