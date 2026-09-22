package kr.hankkitravel.foundation.config;

import java.time.Duration;
import kr.hankkitravel.shared.integration.ExternalHttpClient;
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
}
