package kr.hankkitravel.transit.adapter;

import kr.hankkitravel.shared.integration.ExternalHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KakaoTransitConfiguration {
    @Bean
    KakaoTransitClient kakaoTransitClient(ExternalHttpClient http, KakaoTransitNormalizer normalizer,
            @Value("${hankki.integrations.kakao-base-url}") String baseUrl,
            @Value("${KAKAO_REST_API_KEY:}") String key) {
        return new KakaoTransitClient(http, normalizer, baseUrl, key);
    }
}
