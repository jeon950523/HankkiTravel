package kr.hankkitravel.tourism.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import kr.hankkitravel.tourism.model.TourismLivePlace;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TourApiPlaceDetailParser {
    private final JsonMapper mapper=JsonMapper.builder().build();
    public TourismLivePlace parse(String response) {
        try {
            var raw=mapper.readValue(response,RawResponse.class);
            if(raw==null||raw.response()==null||raw.response().header()==null) throw invalid();
            String code=raw.response().header().resultCode();
            if(!"0000".equals(code)) throw new IntegrationException("TOUR_API",
                    "22".equals(code)?IntegrationFailure.QUOTA_EXCEEDED:IntegrationFailure.UPSTREAM_REJECTED);
            JsonNode items=raw.response().body()==null?null:raw.response().body().items();
            if(items==null||!items.isObject()) throw unavailable();
            JsonNode item=items.get("item");
            if(item==null||item.isNull()) throw unavailable();
            if(item.isArray()) { if(item.isEmpty()) throw unavailable(); item=item.get(0); }
            var value=mapper.treeToValue(item,DetailItem.class);
            if(value.contentid()==null||!value.contentid().matches("[0-9]{1,20}")) throw unavailable();
            Coordinates coordinates=null;
            if(value.mapx()!=null&&!value.mapx().isBlank()&&value.mapy()!=null&&!value.mapy().isBlank()) {
                var x=new BigDecimal(value.mapx()); var y=new BigDecimal(value.mapy());
                if(x.signum()!=0&&y.signum()!=0) coordinates=new Coordinates(x,y);
            }
            return new TourismLivePlace(value.contentid(),value.contenttypeid(),value.title(),value.addr1(),
                    value.firstimage(),coordinates,value.lDongRegnCd(),value.lDongSignguCd());
        } catch(IntegrationException e) { throw e; }
        catch(JacksonException|IllegalArgumentException e) { throw invalid(); }
    }
    private IntegrationException invalid(){return new IntegrationException("TOUR_API",IntegrationFailure.JSON_PARSING_FAILURE);}
    private IntegrationException unavailable(){return new IntegrationException("TOUR_API",IntegrationFailure.UPSTREAM_REJECTED);}
    @JsonIgnoreProperties(ignoreUnknown=true) record RawResponse(Response response){}
    @JsonIgnoreProperties(ignoreUnknown=true) record Response(Header header,Body body){}
    @JsonIgnoreProperties(ignoreUnknown=true) record Header(String resultCode){}
    @JsonIgnoreProperties(ignoreUnknown=true) record Body(JsonNode items){}
    @JsonIgnoreProperties(ignoreUnknown=true) record DetailItem(String contentid,String contenttypeid,String title,String addr1,
            String firstimage,String mapx,String mapy,String lDongRegnCd,String lDongSignguCd){}
}
