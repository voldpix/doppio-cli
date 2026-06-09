package dev.voldpix.doppio.cli;

import dev.voldpix.doppio.http.HttpTransport;
import dev.voldpix.doppio.http.TransportException;
import dev.voldpix.doppio.model.DoppioResponse;
import dev.voldpix.doppio.model.PreparedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DoppioCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void initCreatesSeedAndRequestFilesWithoutOverwriting() throws Exception {
        var out = new StringWriter();
        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("init");

        assertThat(exit).isZero();
        assertThat(tempDir.resolve(".doppio/local.seed")).exists();
        assertThat(tempDir.resolve(".doppio/requests/example.dopo")).exists();
        assertThat(tempDir.resolve(".doppio/requests/test.dopo")).exists();
        assertThat(tempDir.resolve("requests")).doesNotExist();
        assertThat(Files.readString(tempDir.resolve(".doppio/requests/test.dopo")))
            .contains("GET {{BASE_URL}}/get")
            .contains("{{TEST_ID}}");

        Files.writeString(tempDir.resolve(".doppio/local.seed"), "TOKEN=keep-me");
        Files.writeString(tempDir.resolve(".doppio/requests/test.dopo"), "GET https://keep.example.com");
        DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("init");

        assertThat(Files.readString(tempDir.resolve(".doppio/local.seed"))).isEqualTo("TOKEN=keep-me");
        assertThat(Files.readString(tempDir.resolve(".doppio/requests/test.dopo"))).isEqualTo("GET https://keep.example.com");
        assertThat(out.toString()).contains("Initialized Doppio project");
    }

    @Test
    void runRejectsNonDopoFilesWithoutExecutingHttp() throws Exception {
        Files.writeString(tempDir.resolve("request.txt"), "GET https://example.com");
        var err = new StringWriter();
        var transport = new FakeTransport();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(err, true),
            transport
        ).execute("run", "request.txt");

        assertThat(exit).isEqualTo(1);
        assertThat(err.toString()).contains("Only .dopo");
        assertThat(transport.callCount).isZero();
    }

    @Test
    void runPrintsRequestAndResponseReport() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio"));
        Files.writeString(tempDir.resolve(".doppio/local.seed"), "BASE_URL=https://example.com");
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), """
            GET {{BASE_URL}}/get
            -h Accept=application/json
            -q page=1
            """);
        var out = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("run", "auth/login.dopo");

        assertThat(exit).isZero();
        assertThat(out.toString())
            .contains("GET")
            .contains("Request")
            .contains("Response Headers")
            .contains("Response Body")
            .contains("{\"ok\":true}");
    }

    private static class FakeTransport implements HttpTransport {
        private int callCount;

        @Override
        public DoppioResponse execute(PreparedRequest request, Duration timeout) throws TransportException {
            callCount++;
            return new DoppioResponse(
                200,
                Map.of("content-type", java.util.List.of("application/json")),
                "{\"ok\":true}",
                Duration.ofMillis(4)
            );
        }
    }
}
