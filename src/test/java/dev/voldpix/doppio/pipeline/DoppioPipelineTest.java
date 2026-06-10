package dev.voldpix.doppio.pipeline;

import dev.voldpix.doppio.body.BodyProcessor;
import dev.voldpix.doppio.dsl.DslProcessor;
import dev.voldpix.doppio.http.HttpTransport;
import dev.voldpix.doppio.http.RequestPreparer;
import dev.voldpix.doppio.http.TransportException;
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
        Files.writeString(tempDir.resolve(".doppio/default.seed"), """
            BASE_URL=https://example.com
            USERNAME=voldpix
            """);
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), """
            @name Create user
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
        assertThat(transport.lastRequest.name()).isEqualTo("Create user");
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
    void localVariablesOverrideSeedAndEnvironment() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), """
            BASE_URL=https://seed.example.com
            TOKEN=seed-token
            """);
        Files.writeString(tempDir.resolve(".doppio/requests/local.dopo"), """
            @var BASE_URL=https://local.example.com
            @var TOKEN=local-token
            POST {{BASE_URL}}/login
            -h Authorization=Bearer {{TOKEN}}
            <text|
            hello
            |>
            """);
        var transport = new FakeTransport(new DoppioResponse(200, Map.of(), "ok", Duration.ofMillis(2)));

        var report = pipeline(transport).run(Path.of("local.dopo"), tempDir, Map.of(
            "BASE_URL", "https://env.example.com",
            "TOKEN", "env-token"
        ));

        assertThat(report.isSuccess()).isTrue();
        assertThat(transport.lastRequest.uri().toString()).isEqualTo("https://local.example.com/login");
        assertThat(transport.lastRequest.headers())
            .contains(new dev.voldpix.doppio.model.Header("Authorization", "Bearer local-token"))
            .contains(new dev.voldpix.doppio.model.Header("Content-Type", "text/plain; charset=utf-8"));
    }

    @Test
    void legacyLocalSeedIsStillUsedWhenDefaultSeedIsMissing() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests"));
        Files.writeString(tempDir.resolve(".doppio/local.seed"), "BASE_URL=https://legacy.example.com");
        Files.writeString(tempDir.resolve(".doppio/requests/legacy.dopo"), "GET {{BASE_URL}}/health");
        var transport = new FakeTransport(new DoppioResponse(200, Map.of(), "ok", Duration.ofMillis(2)));

        var report = pipeline(transport).run(Path.of("legacy"), tempDir, Map.of());

        assertThat(report.isSuccess()).isTrue();
        assertThat(transport.lastRequest.uri().toString()).isEqualTo("https://legacy.example.com/health");
    }

    @Test
    void previewBuildsPreparedRequestWithoutExecutingHttp() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), """
            BASE_URL=https://seed.example.com
            TOKEN=seed-token
            """);
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), """
            @name Login user
            @var TOKEN=local-token
            POST {{BASE_URL}}/login
            -h Authorization=Bearer {{TOKEN}}
            <form|
            email=user@example.com
            role=admin
            |>
            """);
        var transport = new FakeTransport(new DoppioResponse(200, Map.of(), "ok", Duration.ofMillis(2)));

        var report = pipeline(transport).preview(Path.of("auth/login"), tempDir, Map.of(
            "BASE_URL", "https://env.example.com",
            "TOKEN", "env-token"
        ));

        assertThat(transport.callCount).isZero();
        assertThat(report.requestFile()).isEqualTo(tempDir.resolve(".doppio/requests/auth/login.dopo"));
        assertThat(report.request().name()).isEqualTo("Login user");
        assertThat(report.request().uri().toString()).isEqualTo("https://seed.example.com/login");
        assertThat(report.request().headers())
            .contains(new dev.voldpix.doppio.model.Header("Authorization", "Bearer local-token"))
            .contains(new dev.voldpix.doppio.model.Header("Content-Type", "application/x-www-form-urlencoded"));
        assertThat(report.bodyKind()).isEqualTo(dev.voldpix.doppio.model.BodyKind.FORM);
        assertThat(report.body().content()).isEqualTo("email=user%40example.com&role=admin");
    }

    @Test
    void previewHydratesExpectationsWithoutExecutingHttp() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "EXPECTED=ok");
        Files.writeString(tempDir.resolve(".doppio/requests/expect.dopo"), """
            @expect status=200
            @expect header Content-Type contains json
            @expect body contains {{EXPECTED}}
            GET https://example.com
            """);
        var transport = new FakeTransport(new DoppioResponse(200, Map.of(), "ok", Duration.ofMillis(2)));

        var report = pipeline(transport).preview(Path.of("expect"), tempDir, Map.of());

        assertThat(transport.callCount).isZero();
        assertThat(report.expectations()).hasSize(3);
        assertThat(report.expectations())
            .extracting("expected")
            .containsExactly("200", "json", "ok");
    }

    @Test
    void localVariableValuesAreLiteralAndNotRecursivelyHydrated() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests"));
        Files.writeString(tempDir.resolve(".doppio/requests/literal.dopo"), """
            @var TOKEN="{{HOST}}"
            GET https://example.com
            -h Authorization=Bearer {{TOKEN}}
            """);
        var transport = new FakeTransport(new DoppioResponse(200, Map.of(), "ok", Duration.ofMillis(2)));

        pipeline(transport).run(Path.of("literal.dopo"), tempDir, Map.of());

        assertThat(transport.lastRequest.headers())
            .contains(new dev.voldpix.doppio.model.Header("Authorization", "Bearer {{HOST}}"));
    }

    @Test
    void defaultContentTypeDoesNotOverrideUserContentType() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests"));
        Files.writeString(tempDir.resolve(".doppio/requests/text.dopo"), """
            POST https://example.com
            -h Content-Type=application/custom
            <text|
            hello
            |>
            """);
        var transport = new FakeTransport(new DoppioResponse(200, Map.of(), "ok", Duration.ofMillis(2)));

        pipeline(transport).run(Path.of("text.dopo"), tempDir, Map.of());

        assertThat(transport.lastRequest.headers())
            .containsExactly(new dev.voldpix.doppio.model.Header("Content-Type", "application/custom"));
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
    void runEvaluatesBasicExpectations() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests"));
        Files.writeString(tempDir.resolve(".doppio/requests/success.dopo"), """
            @expect status=201
            @expect header Content-Type contains json
            @expect body contains created
            GET https://example.com
            """);
        var transport = new FakeTransport(new DoppioResponse(
            201,
            Map.of("Content-Type", List.of("application/json")),
            "{\"status\":\"created\"}",
            Duration.ofMillis(2)
        ));

        var report = pipeline(transport).run(Path.of("success"), tempDir, Map.of());

        assertThat(report.isSuccess()).isTrue();
        assertThat(report.expectations().passedCount()).isEqualTo(3);
        assertThat(report.expectations().failedCount()).isZero();
    }

    @Test
    void runFailsWhenExpectationFailsEvenIfHttpStatusIs2xx() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests"));
        Files.writeString(tempDir.resolve(".doppio/requests/fail-expect.dopo"), """
            @expect status=200
            @expect body contains missing
            GET https://example.com
            """);
        var transport = new FakeTransport(new DoppioResponse(
            200,
            Map.of(),
            "{\"ok\":true}",
            Duration.ofMillis(2)
        ));

        var report = pipeline(transport).run(Path.of("fail-expect"), tempDir, Map.of());

        assertThat(report.response().isSuccess()).isTrue();
        assertThat(report.isSuccess()).isFalse();
        assertThat(report.expectations().passedCount()).isEqualTo(1);
        assertThat(report.expectations().failedCount()).isEqualTo(1);
        assertThat(report.expectations().results().getLast().message()).contains("response body did not contain");
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
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "BASE_URL=https://example.com");
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), "GET {{BASE_URL}}/login");
        var transport = new FakeTransport(new DoppioResponse(200, Map.of(), "ok", Duration.ofMillis(2)));

        var report = pipeline(transport).run(Path.of("auth/login.dopo"), tempDir.resolve(".doppio"), Map.of());

        assertThat(report.isSuccess()).isTrue();
        assertThat(transport.lastRequest.uri().toString()).isEqualTo("https://example.com/login");
    }

    @Test
    void runInsideDoppioProjectAllowsOptionalDopoExtension() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "BASE_URL=https://example.com");
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), "GET {{BASE_URL}}/login");
        var transport = new FakeTransport(new DoppioResponse(200, Map.of(), "ok", Duration.ofMillis(2)));

        var report = pipeline(transport).run(Path.of("auth/login"), tempDir, Map.of());

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

        assertThatThrownBy(() -> pipeline(transport).run(Path.of("standalone"), tempDir, Map.of("TOKEN", "env-token")))
            .isInstanceOf(DoppioException.class)
            .hasMessageContaining("Only .dopo");
    }

    private DoppioPipeline pipeline(HttpTransport transport) {
        return new DoppioPipeline(
            new RequestFileResolver(),
            new SeedFileLoader(),
            new TemplateEngine(),
            new DslProcessor(),
            new BodyProcessor(),
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
