package kr.hankkitravel.foundation.config;

import java.time.Duration;
import kr.hankkitravel.shared.integration.ExternalHttpClient;
import kr.hankkitravel.tourism.adapter.TourApiClient;
import kr.hankkitravel.tourism.adapter.TourApiParser;
import kr.hankkitravel.transit.adapter.KakaoTransitClient;
import kr.hankkitravel.transit.adapter.KakaoTransitNormalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IntegrationConfiguration {
    @Bean
    ExternalHttpClient externalHttpClient(
            @Value("${hankki.integrations.connect-timeout}") Duration connectTimeout,
            @Value("${hankki.integrations.request-timeout}") Duration requestTimeout) {
        return new ExternalHttpClient(connectTimeout, requestTimeout);
    }

    @Bean
    TourApiClient tourApiClient(ExternalHttpClient http, TourApiParser parser,
            @Value("${hankki.integrations.tour-api-base-url}") String baseUrl,
            @Value("${DATA_GO_KR_SERVICE_KEY:}") String key) {
        return new TourApiClient(http, parser, baseUrl, key);
    }

    @Bean
    KakaoTransitClient kakaoTransitClient(ExternalHttpClient http, KakaoTransitNormalizer normalizer,
            @Value("${hankki.integrations.kakao-base-url}") String baseUrl,
            @Value("${KAKAO_REST_API_KEY:}") String key) {
        return new KakaoTransitClient(http, normalizer, baseUrl, key);
    }
}
