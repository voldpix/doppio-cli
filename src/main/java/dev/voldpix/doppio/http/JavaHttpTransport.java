package dev.voldpix.doppio.http;

import dev.voldpix.doppio.model.DoppioResponse;
import dev.voldpix.doppio.model.PreparedRequest;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class JavaHttpTransport implements HttpTransport {
    private static final String USER_AGENT = "doppio/1.0";

    private final HttpClient client;

    public JavaHttpTransport() {
        this(HttpClient.newHttpClient());
    }

    public JavaHttpTransport(HttpClient client) {
        this.client = client;
    }

    @Override
    public DoppioResponse execute(PreparedRequest request, Duration timeout) throws TransportException {
        var builder = HttpRequest.newBuilder(request.uri())
            .timeout(timeout)
            .header("User-Agent", USER_AGENT);

        request.headers().forEach(header -> builder.header(header.key(), header.value()));

        var publisher = request.hasBody()
            ? HttpRequest.BodyPublishers.ofString(request.body())
            : HttpRequest.BodyPublishers.noBody();

        var httpRequest = builder.method(request.method().name(), publisher).build();
        var started = System.nanoTime();

        try {
            var response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            var duration = Duration.ofNanos(System.nanoTime() - started);
            return new DoppioResponse(response.statusCode(), response.headers().map(), response.body(), duration);
        } catch (IOException e) {
            throw new TransportException("Network request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException("Network request was interrupted", e);
        }
    }
}
