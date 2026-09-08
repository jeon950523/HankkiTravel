package kr.hankkitravel.tourism.adapter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import kr.hankkitravel.tourism.model.TourismPlace;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TourApiParser {
    private final JsonMapper mapper = JsonMapper.builder().build();

    public List<TourismPlace> parse(String json) {
        try {
            var raw = mapper.readValue(json, TourApiRawResponse.class);
            require(raw != null && raw.response() != null && raw.response().header() != null
                    && raw.response().header().resultCode() != null);
            if (!"0000".equals(raw.response().header().resultCode())) {
                throw new IntegrationException("TOUR_API", IntegrationFailure.UPSTREAM_REJECTED);
            }
            var body = raw.response().body();
            require(body != null && body.totalCount() != null && body.totalCount() >= 0);
            var items = body.items();
            if (items == null || items.isNull() || (items.isTextual() && items.asString().isBlank())) {
                require(body.totalCount() == 0);
                return List.of();
            }
            require(items.isObject());
            var node = items.get("item");
            if (node == null || node.isNull()) {
                require(body.totalCount() == 0);
                return List.of();
            }
            var result = new ArrayList<TourismPlace>();
            if (node.isArray()) {
                for (var item : node) result.add(convert(mapper.treeToValue(item, TourApiRawResponse.Item.class)));
            } else {
                require(node.isObject());
                result.add(convert(mapper.treeToValue(node, TourApiRawResponse.Item.class)));
            }
            return List.copyOf(result);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new IntegrationException("TOUR_API", IntegrationFailure.JSON_PARSING_FAILURE);
        }
    }

    private TourismPlace convert(TourApiRawResponse.Item item) {
        require(item != null && item.contentid() != null && !item.contentid().isBlank());
        Coordinates coordinates = null;
        if (item.mapx() != null && !item.mapx().isBlank() && item.mapy() != null && !item.mapy().isBlank()) {
            var x = new BigDecimal(item.mapx());
            var y = new BigDecimal(item.mapy());
            // TourAPI's 0,0 means unavailable location, not a routable Korean place.
            if (x.signum() != 0 && y.signum() != 0) coordinates = new Coordinates(x, y);
        }
        return new TourismPlace(item.contentid(), item.contenttypeid(), item.title(),
                item.addr1(), coordinates, item.modifiedtime());
    }

    private void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Invalid upstream structure");
    }
}
