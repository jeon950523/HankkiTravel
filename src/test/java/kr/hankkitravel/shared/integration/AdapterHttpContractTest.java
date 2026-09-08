package kr.hankkitravel.shared.integration;

import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.tourism.adapter.TourApiClient;
import kr.hankkitravel.tourism.adapter.TourApiParser;
import kr.hankkitravel.transit.adapter.KakaoTransitClient;
import kr.hankkitravel.transit.adapter.KakaoTransitNormalizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdapterHttpContractTest {
    private HttpServer server;
    private String base;
    private final AtomicInteger code = new AtomicInteger(200);
    private final AtomicReference<String> body = new AtomicReference<>("{\"status\":\"NO_RESULTS\"}");
    private final AtomicReference<URI> received = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private ExternalHttpClient http;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            received.set(exchange.getRequestURI());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            var bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code.get(), bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        http = new ExternalHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void kakaoAuthAndWgs84OrderAreExact() {
        var start = new Coordinates(new BigDecimal("126.4724622276"), new BigDecimal("33.5093583102"));
        var end = new Coordinates(new BigDecimal("126.4640748143"), new BigDecimal("33.4788450641"));
        var result = new KakaoTransitClient(http, new KakaoTransitNormalizer(), base, "contract-only")
                .findRoutes(start, end);
        assertThat(auth.get()).isEqualTo("KakaoAK contract-only");
        assertThat(received.get().toString()).isEqualTo("/v2/routing/publictraffic?start_x=126.4724622276&start_y=33.5093583102&end_x=126.4640748143&end_y=33.4788450641");
        assertThat(result.routes()).isEmpty();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test void tourApiUsesExactlyOnceEncodedKeyAndBoundedPage() {
        body.set("{\"response\":{\"header\":{\"resultCode\":\"0000\"},\"body\":{\"items\":\"\",\"totalCount\":0}}}");
        assertThat(new TourApiClient(http, new TourApiParser(), base + "/KorService2", "test+/=key")
                .fetchPage(1, 1)).isEmpty();
        assertThat(received.get().getPath()).isEqualTo("/KorService2/areaBasedList2");
        assertThat(received.get().getRawQuery()).contains("serviceKey=test%2B%2F%3Dkey", "numOfRows=1", "lDongRegnCd=50");
        assertThat(URLDecoder.decode(received.get().getRawQuery(), StandardCharsets.UTF_8)).contains("test+/=key");
        assertThat(auth.get()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"400,HTTP_4XX", "401,HTTP_4XX", "429,HTTP_4XX", "500,HTTP_5XX", "503,HTTP_5XX", "302,UNEXPECTED_HTTP_STATUS"})
    void statusFailuresAreSanitizedAndNeverRetried(int status, IntegrationFailure failure) {
        code.set(status);
        body.set("sensitive-provider-body");
        assertThatThrownBy(() -> http.get("KAKAO", URI.create(base + "/?serviceKey=sensitive-query"), Map.of()))
                .isInstanceOfSatisfying(IntegrationException.class, e -> {
                    assertThat(e.failure()).isEqualTo(failure);
                    assertThat(e.getMessage()).doesNotContain("sensitive", "http");
                    assertThat(e.getCause()).isNull();
                });
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test void closedSocketIsNetworkFailure() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        assertThatThrownBy(() -> http.get("KAKAO", URI.create("http://127.0.0.1:" + port), Map.of()))
                .isInstanceOfSatisfying(IntegrationException.class,
                        e -> assertThat(e.failure()).isEqualTo(IntegrationFailure.NETWORK_FAILURE));
    }

    @Test void stalledServerIsTimeout() throws Exception {
        try (var socket = new ServerSocket(0)) {
            var shortClient = new ExternalHttpClient(Duration.ofMillis(100), Duration.ofMillis(100));
            assertThatThrownBy(() -> shortClient.get("KAKAO", URI.create("http://127.0.0.1:" + socket.getLocalPort()), Map.of()))
                    .isInstanceOfSatisfying(IntegrationException.class,
                            e -> assertThat(e.failure()).isEqualTo(IntegrationFailure.TIMEOUT));
        }
    }

    @Test void absentSecretsNeverSendRequests() {
        var client = new TourApiClient(http, new TourApiParser(), base, "");
        assertThatThrownBy(() -> client.fetchPage(1, 1)).isInstanceOfSatisfying(IntegrationException.class,
                e -> assertThat(e.failure()).isEqualTo(IntegrationFailure.SECRET_NOT_PRESENT));
        var point = new Coordinates(BigDecimal.ZERO, BigDecimal.ZERO);
        assertThatThrownBy(() -> new KakaoTransitClient(http, new KakaoTransitNormalizer(), base, "")
                .findRoutes(point, point)).isInstanceOfSatisfying(IntegrationException.class,
                e -> assertThat(e.failure()).isEqualTo(IntegrationFailure.SECRET_NOT_PRESENT));
        assertThat(calls.get()).isZero();
    }
}
