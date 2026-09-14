package kr.hankkitravel.tourism.adapter;

import kr.hankkitravel.shared.integration.ExternalHttpClient;
import kr.hankkitravel.tourism.application.TourismRealtimeSource;
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

    @Bean
    TourApiRestaurantDetailClient tourApiRestaurantDetailClient(ExternalHttpClient http,
            TourApiRestaurantDetailParser parser, @Value("${hankki.integrations.tour-api-base-url}") String baseUrl,
            @Value("${DATA_GO_KR_SERVICE_KEY:}") String key) {
        return new TourApiRestaurantDetailClient(http, parser, baseUrl, key);
    }

    @Bean
    TourismRealtimeSource tourismRealtimeSource(ExternalHttpClient http, TourApiPageParser pageParser,
            TourApiRestaurantDetailClient detailClient, TourApiRestaurantPresentationParser presentationParser,
            @Value("${hankki.integrations.tour-api-base-url}") String baseUrl,
            @Value("${DATA_GO_KR_SERVICE_KEY:}") String key) {
        return new TourApiRealtimeRestaurantClient(http, pageParser, detailClient, presentationParser, baseUrl, key);
    }
}
