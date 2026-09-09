package kr.hankkitravel.foundation.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfiguration {
    private static final String ADMIN_SYNC_PATH = "/api/admin/tourism-sync/";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${hankki.web-base-url}") String webBaseUrl) throws Exception {
        var healthCors = new CorsConfiguration();
        healthCors.setAllowedOrigins(List.of(webBaseUrl));
        healthCors.setAllowedMethods(List.of("GET"));
        healthCors.setAllowedHeaders(List.of("Accept", "Content-Type"));
        var adminCors = new CorsConfiguration();
        adminCors.setAllowedOrigins(List.of(webBaseUrl));
        adminCors.setAllowedMethods(List.of("GET", "POST"));
        adminCors.setAllowedHeaders(List.of("Accept", "Content-Type", "Authorization"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/actuator/health/**", healthCors);
        source.registerCorsConfiguration("/api/admin/tourism-sync/**", adminCors);
        AuthenticationEntryPoint adminEntryPoint = (request, response, exception) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"tourism-sync\"");
        };
        var forbiddenEntryPoint = new Http403ForbiddenEntryPoint();
        return http.cors(c -> c.configurationSource(source))
                .httpBasic(basic -> basic.authenticationEntryPoint(adminEntryPoint))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            if (isAdminSyncRequest(request)) {
                                adminEntryPoint.commence(request, response, exception);
                            } else {
                                forbiddenEntryPoint.commence(request, response, exception);
                            }
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            var authenticationException = new InsufficientAuthenticationException(
                                    "운영자 인증 또는 권한이 필요합니다.", exception);
                            if (isAdminSyncRequest(request)) {
                                adminEntryPoint.commence(request, response, authenticationException);
                            } else {
                                forbiddenEntryPoint.commence(request, response, authenticationException);
                            }
                        }))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/admin/tourism-sync/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/admin/tourism-sync/status")
                        .hasRole("TOURISM_SYNC_OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/admin/tourism-sync/runs")
                        .hasRole("TOURISM_SYNC_OPERATOR")
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    UserDetailsService tourismSyncOperator(
            @Value("${hankki.tourism-sync.admin.username:}") String username,
            @Value("${hankki.tourism-sync.admin.password:}") String password) {
        var users = new InMemoryUserDetailsManager();
        if (username == null || username.isBlank() || password == null || password.isBlank()) return users;
        users.createUser(User.withUsername(username).password("{noop}" + password)
                .roles("TOURISM_SYNC_OPERATOR").build());
        return users;
    }

    private static boolean isAdminSyncRequest(jakarta.servlet.http.HttpServletRequest request) {
        return request.getRequestURI().contains(ADMIN_SYNC_PATH)
                || request.getServletPath().contains(ADMIN_SYNC_PATH);
    }
}
