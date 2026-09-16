package kr.hankkitravel.recommendation.adapter;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.hankkitravel.recommendation.application.ContactEnrichmentProvider;
import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.shared.integration.ExternalHttpClient;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

public final class KakaoLocalSearchClient implements ContactEnrichmentProvider.SearchSource {
    private final ExternalHttpClient http; private final String baseUrl; private final String key;
    private final JsonMapper mapper = JsonMapper.builder().build();
    public KakaoLocalSearchClient(ExternalHttpClient http, String baseUrl, String key) {
        this.http=http; this.baseUrl=baseUrl; this.key=key;
    }
    @Override public List<ContactEnrichmentProvider.Place> search(String name, Coordinates coordinates) {
        if (key == null || key.isBlank()) throw new IntegrationException("KAKAO_LOCAL", IntegrationFailure.SECRET_NOT_PRESENT);
        var builder=UriComponentsBuilder.fromUriString(baseUrl).path("/v2/local/search/keyword.json")
                .queryParam("query", name).queryParam("category_group_code", "FD6").queryParam("size", 5);
        if (coordinates != null) builder.queryParam("x",coordinates.longitude().toPlainString())
                .queryParam("y",coordinates.latitude().toPlainString()).queryParam("radius",1000).queryParam("sort","distance");
        URI uri=builder.encode().build().toUri();
        String body=http.get("KAKAO_LOCAL",uri,Map.of("Authorization","KakaoAK "+key));
        try {
            var documents=mapper.readTree(body).path("documents");
            if (!documents.isArray()) throw new IllegalArgumentException();
            var result=new ArrayList<ContactEnrichmentProvider.Place>();
            for (var item: documents) {
                Coordinates point=null;
                if (item.path("x").isTextual() && item.path("y").isTextual()) {
                    try { point=new Coordinates(new BigDecimal(item.path("x").asText()),new BigDecimal(item.path("y").asText())); }
                    catch (NumberFormatException ignored) { }
                }
                result.add(new ContactEnrichmentProvider.Place(item.path("place_name").asText(null),item.path("phone").asText(null),
                        item.path("address_name").asText(null),item.path("road_address_name").asText(null),point,
                        item.path("place_url").asText(null),item.path("category_name").asText(null)));
            }
            return List.copyOf(result);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IntegrationException("KAKAO_LOCAL", IntegrationFailure.JSON_PARSING_FAILURE);
        }
    }
}
