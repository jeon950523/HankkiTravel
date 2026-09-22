package kr.hankkitravel.tourism.adapter;

import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import kr.hankkitravel.tourism.model.TourApiPage;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TourApiPageParser {
    private final TourApiParser itemParser;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public TourApiPageParser(TourApiParser itemParser) {
        this.itemParser = itemParser;
    }

    public TourApiPage parse(String json) {
        var items = itemParser.parse(json);
        try {
            var body = mapper.readTree(json).path("response").path("body");
            if (!body.isObject()) throw invalid();
            return new TourApiPage(items, positive(body, "pageNo"), positive(body, "numOfRows"),
                    nonNegative(body, "totalCount"));
        } catch (JacksonException | IllegalArgumentException e) {
            throw new IntegrationException("TOUR_API", IntegrationFailure.JSON_PARSING_FAILURE);
        }
    }

    private int positive(tools.jackson.databind.JsonNode body, String field) {
        int value = nonNegative(body, field);
        if (value < 1) throw invalid();
        return value;
    }

    private int nonNegative(tools.jackson.databind.JsonNode body, String field) {
        var value = body.get(field);
        if (value == null || !value.canConvertToInt() || value.intValue() < 0) throw invalid();
        return value.intValue();
    }

    private IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid page metadata"); }
}
