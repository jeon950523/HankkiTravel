package kr.hankkitravel.recommendation.adapter;

import kr.hankkitravel.recommendation.application.TourismDemandStrengthSource;
import kr.hankkitravel.recommendation.application.TourismResourceDemandSource;
import kr.hankkitravel.recommendation.application.ContactEnrichmentProvider;
import kr.hankkitravel.recommendation.application.KakaoLocalContactEnrichmentProvider;
import kr.hankkitravel.recommendation.application.KakaoLocalStrictMatcher;
import kr.hankkitravel.shared.integration.ExternalHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RecommendationEvidenceConfiguration {
    @Bean TourismDemandStrengthSource tourismDemandStrengthSource(ExternalHttpClient http,
            @Value("${hankki.integrations.demand-strength-base-url}") String baseUrl,
            @Value("${DATA_GO_KR_SERVICE_KEY:}") String key) { return new TourismDemandStrengthClient(http, baseUrl, key); }
    @Bean TourismResourceDemandSource tourismResourceDemandSource(ExternalHttpClient http,
            @Value("${hankki.integrations.resource-demand-base-url}") String baseUrl,
            @Value("${DATA_GO_KR_SERVICE_KEY:}") String key) { return new TourismResourceDemandClient(http, baseUrl, key); }
    @Bean ContactEnrichmentProvider contactEnrichmentProvider(ExternalHttpClient http,
            @Value("${hankki.integrations.kakao-base-url}") String baseUrl,
            @Value("${KAKAO_REST_API_KEY:}") String key,
            @Value("${hankki.recommendation.contact-match.max-distance-meters:120}") double maxDistanceMeters) {
        return new KakaoLocalContactEnrichmentProvider(new KakaoLocalSearchClient(http,baseUrl,key),
                new KakaoLocalStrictMatcher(maxDistanceMeters));
    }
}
