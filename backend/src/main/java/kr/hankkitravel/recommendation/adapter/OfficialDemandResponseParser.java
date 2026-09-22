package kr.hankkitravel.recommendation.adapter;

import java.math.BigDecimal;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class OfficialDemandResponseParser {
    private final JsonMapper mapper = JsonMapper.builder().build();
    BigDecimal normalizedIndex(String provider, String json, String field, String targetDistrict) {
        try {
            JsonNode response = mapper.readTree(json).path("response");
            String resultCode = response.path("header").path("resultCode").asText();
            if (!"0000".equals(resultCode) && !"00".equals(resultCode)) throw invalid(provider);
            JsonNode items = response.path("body").path("items").path("item");
            if (!items.isArray()) return null;
            BigDecimal target=null,min=null,max=null;
            for (JsonNode item:items) {
                String district=item.path("signguCd").asText();
                if (district.isBlank() || "0".equals(district)) continue;
                BigDecimal value=decimal(item.path(field));
                if (value==null) continue;
                min=min==null||value.compareTo(min)<0?value:min;
                max=max==null||value.compareTo(max)>0?value:max;
                if (targetDistrict.equals(district)) target=value;
            }
            if(target==null||min==null||max==null)return null;
            if(max.compareTo(min)==0)return BigDecimal.valueOf(50);
            return target.subtract(min).multiply(BigDecimal.valueOf(100))
                    .divide(max.subtract(min),4,java.math.RoundingMode.HALF_UP);
        } catch (JacksonException | NumberFormatException exception) { throw invalid(provider); }
    }
    private BigDecimal decimal(JsonNode node){
        if(node.isNumber())return node.decimalValue();
        if(node.isTextual()&&!node.asText().isBlank())return new BigDecimal(node.asText());
        return null;
    }
    private IntegrationException invalid(String provider) {
        return new IntegrationException(provider, IntegrationFailure.JSON_PARSING_FAILURE);
    }
}
