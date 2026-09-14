package kr.hankkitravel.tourism.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import kr.hankkitravel.tourism.model.TourismRestaurantPresentation;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TourApiRestaurantPresentationParser {
    private final JsonMapper mapper = JsonMapper.builder().build();

    public TourismRestaurantPresentation parse(String response) {
        if (response != null && response.trim().startsWith("<")) return parseXml(response);
        try {
            var raw = mapper.readValue(response, RawResponse.class);
            requireSuccess(raw == null ? null : raw.response());
            if (raw.response().body() == null || raw.response().body().items() == null
                    || !raw.response().body().items().isObject()) return empty();
            JsonNode item = raw.response().body().items().get("item");
            if (item == null || item.isNull()) return empty();
            if (item.isArray()) {
                if (item.isEmpty()) return empty();
                item = item.get(0);
            }
            if (!item.isObject()) return empty();
            var detail = mapper.treeToValue(item, DetailItem.class);
            return new TourismRestaurantPresentation(detail.title(), detail.addr1(), detail.firstimage());
        } catch (IntegrationException exception) {
            throw exception;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private TourismRestaurantPresentation parseXml(String response) {
        try {
            Document document = xml(response);
            String code = text(document, "resultCode");
            if (!"0000".equals(code)) throw upstream(code);
            Element item = first(document, "item");
            if (item == null) return empty();
            return new TourismRestaurantPresentation(text(item, "title"), text(item, "addr1"), text(item, "firstimage"));
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
    private TourismRestaurantPresentation empty() { return new TourismRestaurantPresentation(null, null, null); }
    private IntegrationException invalid() { return new IntegrationException("TOUR_API", IntegrationFailure.JSON_PARSING_FAILURE); }

    @JsonIgnoreProperties(ignoreUnknown = true) record RawResponse(Response response) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Response(Header header, Body body) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Header(String resultCode) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Body(JsonNode items) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record DetailItem(String title, String addr1, String firstimage) {}
}
