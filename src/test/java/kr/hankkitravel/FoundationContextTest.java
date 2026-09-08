package kr.hankkitravel;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import kr.hankkitravel.foundation.persistence.FoundationMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FoundationContextTest {
    @Autowired FoundationMapper mapper;
    @Autowired Flyway flyway;
    @LocalServerPort int port;

    @Test void contextMigrationAndMyBatis() {
        assertThat(mapper.findVersion()).isEqualTo("P0.1");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
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
}
