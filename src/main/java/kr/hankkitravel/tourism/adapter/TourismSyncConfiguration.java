package kr.hankkitravel.tourism.adapter;

import kr.hankkitravel.shared.integration.ExternalHttpClient;
import kr.hankkitravel.tourism.application.TourismSnapshotSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class TourismSyncConfiguration {
    @Bean
    @Primary
    TourismSnapshotSource tourismSnapshotSource(ExternalHttpClient http, TourApiPageParser parser,
            @Value("${hankki.integrations.tour-api-base-url}") String baseUrl,
            @Value("${DATA_GO_KR_SERVICE_KEY:}") String key) {
        return new TourApiSnapshotClient(http, parser, baseUrl, key);
    }
}
