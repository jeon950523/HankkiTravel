package kr.hankkitravel.shared.integration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

public final class ExternalHttpClient {
    private final HttpClient client;
    private final Duration requestTimeout;

    public ExternalHttpClient(Duration connectTimeout, Duration requestTimeout) {
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.requestTimeout = requestTimeout;
    }

    public String get(String provider, URI uri, Map<String, String> headers) {
        var builder = HttpRequest.newBuilder(uri).timeout(requestTimeout).GET()
                .header("Accept", "application/json");
        headers.forEach(builder::header);
        try {
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code >= 400 && code < 500) throw failure(provider, IntegrationFailure.HTTP_4XX);
            if (code >= 500) throw failure(provider, IntegrationFailure.HTTP_5XX);
            if (code < 200 || code >= 300) throw failure(provider, IntegrationFailure.UNEXPECTED_HTTP_STATUS);
            return response.body();
        } catch (HttpTimeoutException e) {
            throw failure(provider, IntegrationFailure.TIMEOUT);
        } catch (IOException e) {
            throw failure(provider, IntegrationFailure.NETWORK_FAILURE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure(provider, IntegrationFailure.NETWORK_FAILURE);
        }
    }

    private IntegrationException failure(String provider, IntegrationFailure failure) {
        return new IntegrationException(provider, failure);
    }
}
