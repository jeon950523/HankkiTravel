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
    record Body(JsonNode items, Integer totalCount, Integer pageNo, Integer numOfRows) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String contentid, String contenttypeid, String title, String addr1, String addr2,
            String tel, String zipcode, String mapx, String mapy, String firstimage, String firstimage2,
            String cpyrhtDivCd, String lDongRegnCd, String lDongSignguCd, String lclsSystm1,
            String lclsSystm2, String lclsSystm3, String createdtime, String modifiedtime,
            String showflag) {}
}
