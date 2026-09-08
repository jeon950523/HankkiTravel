package kr.hankkitravel.foundation.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${hankki.web-base-url}") String webBaseUrl) throws Exception {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(webBaseUrl));
        cors.setAllowedMethods(List.of("GET"));
        cors.setAllowedHeaders(List.of("Accept", "Content-Type"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/actuator/health/**", cors);
        return http.cors(c -> c.configurationSource(source))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
