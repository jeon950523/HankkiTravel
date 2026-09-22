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

    @Test void healthAndPrometheusArePublicAndOtherEndpointsAreDenied() throws Exception {
        var client = HttpClient.newHttpClient();
        var health = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                .header("Origin", "http://localhost:5175").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"").doesNotContain("password", "components");
        assertThat(health.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:5175");
        var prometheus = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/prometheus"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(prometheus.statusCode()).isEqualTo(200);
        assertThat(prometheus.body()).isNotBlank().contains("# HELP", "# TYPE");
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

    @Test void guestCanCreateReadAndOwnAFamilyProfileWithoutLogin() throws Exception {
        var client = HttpClient.newHttpClient();
        var firstGuest = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/guests"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(firstGuest.statusCode()).isEqualTo(201);
        var guestIdMatcher = java.util.regex.Pattern.compile("\\\"publicId\\\":\\\"([^\\\"]+)\\\"").matcher(firstGuest.body());
        assertThat(guestIdMatcher.find()).isTrue();
        String guestId = guestIdMatcher.group(1);
        String profilePayload = """
                {"name":"부모님과 제주","transportMode":"CAR","parkingPreference":"REQUIRED",
                 "walkingBurdenPreference":"NORMAL","transferPreference":"AVOID","stairsAvoidance":true,
                 "members":[{"nickname":"엄마","continuousWalkingMinutes":20,"stairsPreference":"AVOID",
                 "mealCautions":["SODIUM","INGREDIENT_CHECK"]}]}
                """;
        var created = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/guests/" + guestId + "/profiles"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(profilePayload)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("부모님과 제주", "SODIUM", "INGREDIENT_CHECK");
        var profileIdMatcher = java.util.regex.Pattern.compile("\\\"profileId\\\":(\\d+)").matcher(created.body());
        assertThat(profileIdMatcher.find()).isTrue();
        String profileId = profileIdMatcher.group(1);
        var listed = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/guests/" + guestId + "/profiles"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.body()).contains("부모님과 제주");
        var secondGuest = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/guests"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        var secondGuestMatcher = java.util.regex.Pattern.compile("\\\"publicId\\\":\\\"([^\\\"]+)\\\"").matcher(secondGuest.body());
        assertThat(secondGuestMatcher.find()).isTrue();
        var denied = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/guests/"
                + secondGuestMatcher.group(1) + "/profiles/" + profileId)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(denied.statusCode()).isEqualTo(404);
    }}
