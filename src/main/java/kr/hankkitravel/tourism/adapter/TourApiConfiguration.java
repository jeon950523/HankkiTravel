package kr.hankkitravel.tourism.adapter;

import kr.hankkitravel.shared.integration.ExternalHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TourApiConfiguration {
    @Bean
    TourApiClient tourApiClient(ExternalHttpClient http, TourApiParser parser,
            @Value("${hankki.integrations.tour-api-base-url}") String baseUrl,
            @Value("${DATA_GO_KR_SERVICE_KEY:}") String key) {
        return new TourApiClient(http, parser, baseUrl, key);
    }
}
