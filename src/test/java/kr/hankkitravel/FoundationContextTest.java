package kr.hankkitravel;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import kr.hankkitravel.foundation.persistence.FoundationMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "hankki.tourism-sync.admin.enabled=true",
        "hankki.tourism-sync.admin.username=operator-test-user",
        "hankki.tourism-sync.admin.password=operator-test-password",
        "hankki.tourism-sync.admin.daily-call-budget=10" })
@ActiveProfiles("test")
class FoundationContextTest {
    @Autowired FoundationMapper mapper;
    @Autowired Flyway flyway;
    @LocalServerPort int port;
    @Value("${hankki.tourism-sync.page-size}") int tourismSyncPageSize;

    @Test void contextMigrationAndMyBatis() {
        assertThat(mapper.findVersion()).isEqualTo("P0.1");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test void tourismSyncPageSizeDefaultsToOneHundred() {
        assertThat(tourismSyncPageSize).isEqualTo(100);
    }

    @Test void healthIsPublicAndOtherEndpointsAreDenied() throws Exception {
        var client = HttpClient.newHttpClient();
        var health = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                .header("Origin", "http://localhost:5175").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"").doesNotContain("password", "components");
        assertThat(health.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:5175");
        var denied = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/env"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(denied.statusCode()).isEqualTo(403);
    }

    @Test void adminSyncStatusRequiresTheConfiguredOperator() throws Exception {
        var client = HttpClient.newHttpClient();
        var endpoint = URI.create("http://localhost:" + port + "/api/admin/tourism-sync/status");
        var unauthenticated = client.send(HttpRequest.newBuilder(endpoint).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(unauthenticated.statusCode()).isEqualTo(401);
        assertThat(unauthenticated.headers().firstValue("WWW-Authenticate")).contains("Basic realm=\"tourism-sync\"");

        String credential = Base64.getEncoder().encodeToString("operator-test-user:operator-test-password".getBytes());
        var authenticated = client.send(HttpRequest.newBuilder(endpoint)
                .header("Authorization", "Basic " + credential).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(authenticated.statusCode()).isEqualTo(200);
        assertThat(authenticated.body()).contains("\"operatorEnabled\":true", "\"dailyCallBudget\":10")
                .doesNotContain("operator-test-password");
    }
}
