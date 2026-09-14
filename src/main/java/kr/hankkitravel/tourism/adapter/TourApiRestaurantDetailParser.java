package kr.hankkitravel.tourism.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import kr.hankkitravel.tourism.model.TourismRestaurantDetail;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TourApiRestaurantDetailParser {
    private final JsonMapper mapper = JsonMapper.builder().build();

    public TourismRestaurantDetail parse(String response) {
        if (response != null && response.trim().startsWith("<")) return parseXml(response);
        try {
            var raw = mapper.readValue(response, RawResponse.class);
            requireSuccess(raw == null ? null : raw.response());
            if (raw.response().body() == null || raw.response().body().items() == null) return empty();
            JsonNode items = raw.response().body().items();
            if (!items.isObject()) return empty();
            var node = items.get("item");
            if (node == null || node.isNull()) return empty();
            if (node.isArray()) {
                if (node.isEmpty()) return empty();
                node = node.get(0);
            }
            if (!node.isObject()) return empty();
            var item = mapper.treeToValue(node, DetailItem.class);
            return new TourismRestaurantDetail(item.firstmenu(), item.treatmenu(), item.opentimefood(),
                    item.restdatefood(), item.parkingfood());
        } catch (IntegrationException exception) {
            throw exception;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private TourismRestaurantDetail parseXml(String response) {
        try {
            Document document = xml(response);
            String code = text(document, "resultCode");
            if (!"0000".equals(code)) throw upstream(code);
            Element item = first(document, "item");
            if (item == null) return empty();
            return new TourismRestaurantDetail(text(item, "firstmenu"), text(item, "treatmenu"),
                    text(item, "opentimefood"), text(item, "restdatefood"), text(item, "parkingfood"));
        } catch (IntegrationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private void requireSuccess(Response response) {
        if (response == null || response.header() == null) throw invalid();
        if (!"0000".equals(response.header().resultCode())) throw upstream(response.header().resultCode());
    }
    private IntegrationException upstream(String code) {
        return new IntegrationException("TOUR_API", "22".equals(code) ? IntegrationFailure.QUOTA_EXCEEDED : IntegrationFailure.UPSTREAM_REJECTED);
    }
    private Document xml(String response) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(response)));
    }
    private Element first(Document document, String name) {
        var nodes = document.getElementsByTagName(name);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }
    private String text(Document document, String name) {
        var nodes = document.getElementsByTagName(name);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }
    private String text(Element element, String name) {
        var nodes = element.getElementsByTagName(name);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }
    private TourismRestaurantDetail empty() { return new TourismRestaurantDetail(null, null, null, null, null); }
    private IntegrationException invalid() { return new IntegrationException("TOUR_API", IntegrationFailure.JSON_PARSING_FAILURE); }

    @JsonIgnoreProperties(ignoreUnknown = true) record RawResponse(Response response) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Response(Header header, Body body) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Header(String resultCode) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Body(JsonNode items) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DetailItem(String firstmenu, String treatmenu, String opentimefood, String restdatefood, String parkingfood) {}
}
