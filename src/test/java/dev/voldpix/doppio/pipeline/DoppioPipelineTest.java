package dev.voldpix.doppio.pipeline;

import dev.voldpix.doppio.dsl.DslProcessor;
import dev.voldpix.doppio.http.HttpTransport;
import dev.voldpix.doppio.http.RequestPreparer;
import dev.voldpix.doppio.http.TransportException;
import dev.voldpix.doppio.json.JsonBodyProcessor;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.DoppioResponse;
import dev.voldpix.doppio.model.PreparedRequest;
import dev.voldpix.doppio.seed.SeedFileLoader;
import dev.voldpix.doppio.template.TemplateEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DoppioPipelineTest {
    @TempDir
    Path tempDir;

    @Test
    void runHydratesSeedAndEnvironmentThenExecutesPreparedRequest() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio"));
        Files.writeString(tempDir.resolve(".doppio/local.seed"), """
            BASE_URL=https://example.com
            USERNAME=voldpix
            """);
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), """
            POST {{BASE_URL}}/users?existing=1
            -h Content-Type=application/json
            -h Authorization=Bearer {{TOKEN}}
            -q existing=2
            -q user={{USERNAME}}

            <|
            {"name":"{{USERNAME}}"}
            |>
            """);
        var transport = new FakeTransport(new DoppioResponse(
            201,
            Map.of("content-type", List.of("application/json")),
            "{\"ok\":true}",
            Duration.ofMillis(12)
        ));
        var pipeline = pipeline(transport);

        var report = pipeline.run(Path.of("auth/login.dopo"), tempDir, Map.of("TOKEN", "env-token"));

        assertThat(report.isSuccess()).isTrue();
        assertThat(transport.callCount).isEqualTo(1);
        assertThat(transport.lastRequest.uri().toString())
            .isEqualTo("https://example.com/users?existing=2&user=voldpix");
        assertThat(transport.lastRequest.headers())
            .extracting("key")
            .containsExactly("Content-Type", "Authorization");
        assertThat(transport.lastRequest.headers())
            .extracting("value")
            .contains("application/json", "Bearer env-token");
        assertThat(transport.lastRequest.body()).isEqualTo("{\"name\":\"voldpix\"}");
    }

    @Test
    void non2xxResponseIsReturnedAsFailedReport() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests"));
        Files.writeString(tempDir.resolve(".doppio/requests/fail.dopo"), "GET https://example.com");
        var transport = new FakeTransport(new DoppioResponse(500, Map.of(), "boom", Duration.ofMillis(3)));

        var report = pipeline(transport).run(Path.of("fail.dopo"), tempDir, Map.of());

        assertThat(report.isSuccess()).isFalse();
        assertThat(report.response().body()).isEqualTo("boom");
    }

    @Test
    void doesNotExecuteHttpWhenTemplateOrJsonFails() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests"));
        Files.writeString(tempDir.resolve(".doppio/requests/missing-template.dopo"), "GET https://example.com/{{MISSING}}");
        var transport = new FakeTransport(new DoppioResponse(200, Map.of(), "", Duration.ZERO));

        assertThatThrownBy(() -> pipeline(transport).run(Path.of("missing-template.dopo"), tempDir, Map.of()))
            .isInstanceOf(DoppioException.class)
            .hasMessageContaining("MISSING");
        assertThat(transport.callCount).isZero();

        Files.writeString(tempDir.resolve(".doppio/requests/invalid-json.dopo"), """
            POST https://example.com
            <|
            {"name":}
            |>
            """);

        assertThatThrownBy(() -> pipeline(transport).run(Path.of("invalid-json.dopo"), tempDir, Map.of()))
            .isInstanceOf(DoppioException.class)
            .hasMessageContaining("expected JSON value");
        assertThat(transport.callCount).isZero();
    }

    @Test
    void runFromInsideDoppioDirectoryUsesRequestsFolderShorthand() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        Files.writeString(tempDir.resolve(".doppio/local.seed"), "BASE_URL=https://example.com");
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), "GET {{BASE_URL}}/login");
        var transport = new FakeTransport(new DoppioResponse(200, Map.of(), "ok", Duration.ofMillis(2)));

        var report = pipeline(transport).run(Path.of("auth/login.dopo"), tempDir.resolve(".doppio"), Map.of());

        assertThat(report.isSuccess()).isTrue();
        assertThat(transport.lastRequest.uri().toString()).isEqualTo("https://example.com/login");
    }

    @Test
    void runOutsideDoppioProjectAllowsDirectFileOnly() throws Exception {
        Files.writeString(tempDir.resolve("standalone.dopo"), "GET https://standalone.example.com/{{TOKEN}}");
        var transport = new FakeTransport(new DoppioResponse(200, Map.of(), "ok", Duration.ofMillis(2)));

        var report = pipeline(transport).run(Path.of("standalone.dopo"), tempDir, Map.of("TOKEN", "env-token"));

        assertThat(report.isSuccess()).isTrue();
        assertThat(transport.lastRequest.uri().toString()).isEqualTo("https://standalone.example.com/env-token");

        assertThatThrownBy(() -> pipeline(transport).run(Path.of("missing.dopo"), tempDir, Map.of()))
            .isInstanceOf(DoppioException.class)
            .hasMessageContaining("No .doppio project found");
    }

    private DoppioPipeline pipeline(HttpTransport transport) {
        return new DoppioPipeline(
            new RequestFileResolver(),
            new SeedFileLoader(),
            new TemplateEngine(),
            new DslProcessor(),
            new JsonBodyProcessor(),
            new RequestPreparer(),
            transport,
            Duration.ofSeconds(30)
        );
    }

    private static class FakeTransport implements HttpTransport {
        private final DoppioResponse response;
        private PreparedRequest lastRequest;
        private int callCount;

        private FakeTransport(DoppioResponse response) {
            this.response = response;
        }

        @Override
        public DoppioResponse execute(PreparedRequest request, Duration timeout) throws TransportException {
            this.lastRequest = request;
            this.callCount++;
            assertThat(timeout).isEqualTo(Duration.ofSeconds(30));
            return response;
        }
    }
}
