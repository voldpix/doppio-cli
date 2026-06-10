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
        assertThat(tempDir.resolve(".doppio/default.seed")).exists();
        assertThat(tempDir.resolve(".doppio/seeds")).isDirectory();
        assertThat(tempDir.resolve(".doppio/local.seed")).doesNotExist();
        assertThat(tempDir.resolve(".doppio/recipes/example.dopo")).exists();
        assertThat(tempDir.resolve(".doppio/recipes/test.dopo")).exists();
        assertThat(tempDir.resolve("recipes")).doesNotExist();
        assertThat(Files.readString(tempDir.resolve(".doppio/recipes/test.dopo")))
            .contains("@name Smoke test")
            .contains("GET {{BASE_URL}}/get")
            .contains("{{TEST_ID}}");
        assertThat(out.toString())
            .contains("Initialized Doppio project")
            .contains(tempDir.resolve(".doppio").toAbsolutePath().normalize().toString())
            .contains("|-- default.seed")
            .contains("|-- seeds/")
            .contains("`-- recipes/")
            .contains("|-- example.dopo")
            .contains("`-- test.dopo");

        Files.writeString(tempDir.resolve(".doppio/default.seed"), "TOKEN=keep-me");
        Files.writeString(tempDir.resolve(".doppio/recipes/test.dopo"), "GET https://keep.example.com");
        DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("init");

        assertThat(Files.readString(tempDir.resolve(".doppio/default.seed"))).isEqualTo("TOKEN=keep-me");
        assertThat(Files.readString(tempDir.resolve(".doppio/recipes/test.dopo"))).isEqualTo("GET https://keep.example.com");
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
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "BASE_URL=https://example.com");
        Files.createDirectories(tempDir.resolve(".doppio/recipes/auth"));
        Files.writeString(tempDir.resolve(".doppio/recipes/auth/login.dopo"), """
            @name Login request
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
        ).execute("run", "auth/login");

        assertThat(exit).isZero();
        assertThat(out.toString())
            .contains("GET")
            .contains("Login request")
            .contains("Request Details")
            .contains("Response Headers")
            .contains("Response Body")
            .contains("\"ok\": true");
    }

    @Test
    void runCanPrintJsonReportForAgents() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes/auth"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "BASE_URL=https://example.com");
        Files.writeString(tempDir.resolve(".doppio/recipes/auth/login.dopo"), """
            @name Login request
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
        ).execute("run", "--json", "auth/login");

        assertThat(exit).isZero();
        assertThat(out.toString())
            .contains("\"kind\":\"run\"")
            .contains("\"success\":true")
            .contains("\"name\":\"Login request\"")
            .contains("\"url\":\"https://example.com/get?page=1\"")
            .contains("\"status\":200")
            .contains("\"body\":\"{\\\"ok\\\":true}\"")
            .doesNotContain("\u001B[");
    }

    @Test
    void runCanUseSelectedEnvironment() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/seeds"));
        Files.createDirectories(tempDir.resolve(".doppio/recipes/auth"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "BASE_URL=https://default.example.com");
        Files.writeString(tempDir.resolve(".doppio/seeds/dev.seed"), "BASE_URL=https://dev.example.com");
        Files.writeString(tempDir.resolve(".doppio/recipes/auth/login.dopo"), """
            @name Login request
            GET {{BASE_URL}}/get
            """);
        var out = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("run", "auth/login", "--env", "dev");

        assertThat(exit).isZero();
        assertThat(out.toString())
            .contains("Seed: dev")
            .contains("https://dev.example.com/get");
    }

    @Test
    void shellHelpIsAvailable() {
        var out = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("shell", "--help");

        assertThat(exit).isZero();
        assertThat(out.toString())
            .contains("Usage: doppio shell")
            .contains("--project")
            .contains("--env");
    }

    @Test
    void runPrintsExpectationResults() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        Files.writeString(tempDir.resolve(".doppio/recipes/expect.dopo"), """
            @expect status=200
            @expect header content-type contains json
            @expect body contains ok
            GET https://example.com/get
            """);
        var out = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("run", "expect");

        assertThat(exit).isZero();
        assertThat(out.toString())
            .contains("Expectations")
            .contains("Passed: 3")
            .contains("Failed: 0")
            .contains("status=200")
            .contains("header content-type contains json")
            .contains("body contains ok");
    }

    @Test
    void runJsonFailsWhenExpectationFails() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        Files.writeString(tempDir.resolve(".doppio/recipes/expect-fail.dopo"), """
            @expect status=200
            @expect body contains missing
            GET https://example.com/get
            """);
        var out = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("run", "--json", "expect-fail");

        assertThat(exit).isEqualTo(1);
        assertThat(out.toString())
            .contains("\"kind\":\"run\"")
            .contains("\"success\":false")
            .contains("\"response\":{\"status\":200,\"success\":true")
            .contains("\"expectations\":{\"success\":false")
            .contains("\"failed\":1")
            .contains("\"label\":\"body contains missing\"")
            .contains("response body did not contain missing");
    }

    @Test
    void runCanSaveReportNextToResolvedRequestFile() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes/auth"));
        Files.writeString(tempDir.resolve(".doppio/recipes/auth/login.dopo"), """
            @name Login request
            GET https://example.com/get
            """);
        var out = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("run", "--save", "auth/login.dopo");

        assertThat(exit).isZero();
        assertThat(out.toString()).contains("Saved report:");
        var reports = Files.list(tempDir.resolve(".doppio/recipes/auth"))
            .filter(path -> path.getFileName().toString().matches("login-\\d{13}\\.txt"))
            .toList();
        assertThat(reports).hasSize(1);
        assertThat(Files.readString(reports.getFirst()))
            .contains("Doppio Run")
            .contains("Login request")
            .contains("Response Body")
            .doesNotContain("\u001B[");
    }

    @Test
    void genCreatesEditableRequestPlaceholderUnderRequestsFolder() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        var out = new StringWriter();
        var err = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(err, true),
            new FakeTransport()
        ).execute("gen", "auth/login");

        var requestFile = tempDir.resolve(".doppio/recipes/auth/login.dopo");
        assertThat(exit).isZero();
        assertThat(err.toString()).isBlank();
        assertThat(requestFile).exists();
        assertThat(Files.readString(requestFile))
            .contains("@name Login")
            .contains("POST {{BASE_URL}}/path")
            .contains("-h Content-Type=application/json")
            .contains("<json|");
        assertThat(out.toString())
            .contains("Created recipe")
            .contains("auth/login.dopo");
    }

    @Test
    void genCanCreateEnvironmentSeedFiles() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        var out = new StringWriter();
        var err = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(err, true),
            new FakeTransport()
        ).execute("gen", "--env", "dev");

        var envFile = tempDir.resolve(".doppio/seeds/dev.seed");
        assertThat(exit).isZero();
        assertThat(err.toString()).isBlank();
        assertThat(envFile).exists();
        assertThat(Files.readString(envFile))
            .contains("--env dev")
            .contains("BASE_URL=https://api.dev.example.com")
            .contains("TOKEN=");
        assertThat(out.toString())
            .contains("Created seed")
            .contains("seeds/dev.seed");

        err.getBuffer().setLength(0);
        var overwriteExit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(err, true),
            new FakeTransport()
        ).execute("gen", "--env", "dev");

        assertThat(overwriteExit).isEqualTo(1);
        assertThat(err.toString()).contains("Seed already exists: dev");

        err.getBuffer().setLength(0);
        var mixedExit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(err, true),
            new FakeTransport()
        ).execute("gen", "--env", "staging", "auth/login");

        assertThat(mixedExit).isEqualTo(1);
        assertThat(err.toString()).contains("--env cannot be combined");
        assertThat(tempDir.resolve(".doppio/seeds/staging.seed")).doesNotExist();

        err.getBuffer().setLength(0);
        var defaultExit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(err, true),
            new FakeTransport()
        ).execute("gen", "--env", "default");

        assertThat(defaultExit).isEqualTo(1);
        assertThat(err.toString()).contains("built-in seed");
        assertThat(tempDir.resolve(".doppio/seeds/default.seed")).doesNotExist();
    }

    @Test
    void genMethodGetCreatesNoBodyByDefault() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("gen", "users/me", "--method", "GET");

        assertThat(exit).isZero();
        var content = Files.readString(tempDir.resolve(".doppio/recipes/users/me.dopo"));
        assertThat(content)
            .contains("@name Me")
            .contains("GET {{BASE_URL}}/path")
            .doesNotContain("Content-Type")
            .doesNotContain("<json|")
            .doesNotContain("<text|")
            .doesNotContain("<csv|")
            .doesNotContain("<form|");
    }

    @Test
    void genMethodPostCanExplicitlyCreateNoBody() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("gen", "jobs/start", "--method", "POST", "--body", "none");

        assertThat(exit).isZero();
        var content = Files.readString(tempDir.resolve(".doppio/recipes/jobs/start.dopo"));
        assertThat(content)
            .contains("POST {{BASE_URL}}/path")
            .doesNotContain("Content-Type")
            .doesNotContain("<json|")
            .doesNotContain("<text|")
            .doesNotContain("<csv|")
            .doesNotContain("<form|");
    }

    @Test
    void genCreatesTypedBodyPlaceholdersAndDefaultContentTypes() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));

        assertGeneratedBody("json-request", "json", "<json|", "\"key\": \"value\"", "Content-Type=application/json");
        assertGeneratedBody("text-request", "text", "<text|", "hello", "Content-Type=text/plain; charset=utf-8");
        assertGeneratedBody("csv-request", "csv", "<csv|", "name,value", "Content-Type=text/csv; charset=utf-8");
        assertGeneratedBody("form-request", "form", "<form|", "key=value", "Content-Type=application/x-www-form-urlencoded");
    }

    @Test
    void genAddsBearerHeadersAndQueries() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute(
            "gen",
            "users/search",
            "--method",
            "GET",
            "--bearer",
            "-H",
            "X-Client=doppio",
            "--header",
            "Accept=application/json",
            "-q",
            "page=1",
            "--query",
            "flag"
        );

        assertThat(exit).isZero();
        assertThat(Files.readString(tempDir.resolve(".doppio/recipes/users/search.dopo")))
            .contains("GET {{BASE_URL}}/path")
            .contains("-h Authorization=Bearer {{TOKEN}}")
            .contains("-h X-Client=doppio")
            .contains("-h Accept=application/json")
            .contains("-q page=1")
            .contains("-q flag")
            .doesNotContain("Content-Type");
    }

    @Test
    void genDoesNotInjectDefaultContentTypeWhenUserProvidesOne() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("gen", "custom/content", "--body", "json", "-H", "Content-Type=application/custom");

        assertThat(exit).isZero();
        assertThat(Files.readString(tempDir.resolve(".doppio/recipes/custom/content.dopo")))
            .contains("-h Content-Type=application/custom")
            .doesNotContain("-h Content-Type=application/json");
    }

    @Test
    void genRejectsInvalidHelperOptionsWithoutCreatingFiles() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));

        assertInvalidGen("bad-method", "Unsupported method", "gen", "bad-method", "--method", "FETCH");
        assertInvalidGen("bad-body", "Unsupported body kind", "gen", "bad-body", "--body", "xml");
        assertInvalidGen("bad-header", "Header must use key=value", "gen", "bad-header", "-H", "Authorization");
        assertInvalidGen("bad-header-name", "Invalid HTTP header name", "gen", "bad-header-name", "-H", "Bad Header=value");
        assertInvalidGen("bad-query", "Query param key is missing", "gen", "bad-query", "-q", "=broken");
    }

    @Test
    void genRejectsOverwritesAndInternalRequestsPrefix() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        Files.writeString(tempDir.resolve(".doppio/recipes/existing.dopo"), "GET https://example.com");
        var err = new StringWriter();

        var overwriteExit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(err, true),
            new FakeTransport()
        ).execute("gen", "existing.dopo");

        assertThat(overwriteExit).isEqualTo(1);
        assertThat(err.toString()).contains("Request already exists");
        assertThat(Files.readString(tempDir.resolve(".doppio/recipes/existing.dopo")))
            .isEqualTo("GET https://example.com");

        err.getBuffer().setLength(0);
        var prefixedExit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(err, true),
            new FakeTransport()
        ).execute("gen", "recipes/auth/login");

        assertThat(prefixedExit).isEqualTo(1);
        assertThat(err.toString()).contains("without .doppio/recipes");
    }

    @Test
    void genFromCurlCreatesRequestFromBasicCurlCommand() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        var out = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute(
            "gen",
            "auth/login",
            "--from-curl",
            """
            curl --location --request POST 'https://api.example.com/auth/login?source=docs&debug' \
              --header 'Content-Type: application/json' \
              --header 'Authorization: Bearer token' \
              --data-raw '{"email":"me@example.com"}'
            """
        );

        assertThat(exit).isZero();
        assertThat(out.toString()).contains("Created recipe").contains("auth/login.dopo");
        assertThat(Files.readString(tempDir.resolve(".doppio/recipes/auth/login.dopo"))).isEqualTo("""
            @name Login
            POST https://api.example.com/auth/login
            -h Content-Type=application/json
            -h Authorization=Bearer token
            -q source=docs
            -q debug

            <json|
            {"email":"me@example.com"}
            |>
            """);
    }

    @Test
    void genFromCurlCreatesFormBodyAndDefaultContentType() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute(
            "gen",
            "auth/form-login",
            "--from-curl",
            "curl https://api.example.com/login -H 'Accept: application/json' -d username=alice -d password=secret"
        );

        assertThat(exit).isZero();
        assertThat(Files.readString(tempDir.resolve(".doppio/recipes/auth/form-login.dopo"))).isEqualTo("""
            @name Form Login
            POST https://api.example.com/login
            -h Content-Type=application/x-www-form-urlencoded
            -h Accept=application/json

            <form|
            username=alice
            password=secret
            |>
            """);
    }

    @Test
    void genFromCurlRejectsUnsupportedOptionsAndHelperMixing() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        var err = new StringWriter();

        var unsupportedExit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(err, true),
            new FakeTransport()
        ).execute("gen", "bad/import", "--from-curl", "curl -I https://api.example.com");

        assertThat(unsupportedExit).isEqualTo(1);
        assertThat(err.toString()).contains("Unsupported curl option: -I");
        assertThat(tempDir.resolve(".doppio/recipes/bad/import.dopo")).doesNotExist();

        err.getBuffer().setLength(0);
        var mixedExit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(err, true),
            new FakeTransport()
        ).execute("gen", "bad/mixed", "--from-curl", "curl https://api.example.com", "--method", "POST");

        assertThat(mixedExit).isEqualTo(1);
        assertThat(err.toString()).contains("--from-curl cannot be combined");
        assertThat(tempDir.resolve(".doppio/recipes/bad/mixed.dopo")).doesNotExist();
    }

    @Test
    void showPrintsRequestDetailsWithoutExecutingHttp() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes/auth"));
        Files.writeString(tempDir.resolve(".doppio/recipes/auth/login.dopo"), """
            @name Login user
            @var EMAIL=user@example.com
            @expect status=200
            @expect body contains ok
            POST {{BASE_URL}}/auth/login
            -h Authorization=Bearer {{TOKEN}}
            -q source=doppio
            <json|
            {
              "email": "{{EMAIL}}"
            }
            |>
            """);
        var out = new StringWriter();
        var transport = new FakeTransport();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            transport
        ).execute("show", "auth/login");

        assertThat(exit).isZero();
        assertThat(transport.callCount).isZero();
        assertThat(out.toString())
            .contains("Request")
            .contains("Name: Login user")
            .contains("File: auth/login.dopo")
            .contains("Method: POST")
            .contains("URL: {{BASE_URL}}/auth/login")
            .contains("Body: JSON")
            .contains("Authorization=Bearer {{TOKEN}}")
            .contains("source=doppio")
            .contains("EMAIL=user@example.com")
            .contains("Expectations")
            .contains("status=200")
            .contains("body contains ok");
    }

    @Test
    void showCanPrintJsonInspectionWithoutExecutingHttp() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes/auth"));
        Files.writeString(tempDir.resolve(".doppio/recipes/auth/login.dopo"), """
            @name Login user
            @var EMAIL=user@example.com
            @expect body contains ok
            POST {{BASE_URL}}/auth/login
            -h Authorization=Bearer {{TOKEN}}
            -q source=doppio
            <json|
            {"email":"{{EMAIL}}"}
            |>
            """);
        var out = new StringWriter();
        var transport = new FakeTransport();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            transport
        ).execute("show", "--json", "auth/login");

        assertThat(exit).isZero();
        assertThat(transport.callCount).isZero();
        assertThat(out.toString())
            .contains("\"kind\":\"show\"")
            .contains("\"relativePath\":\"auth/login.dopo\"")
            .contains("\"name\":\"Login user\"")
            .contains("\"url\":\"{{BASE_URL}}/auth/login\"")
            .contains("\"kind\":\"JSON\"")
            .contains("\"EMAIL\":\"user@example.com\"")
            .contains("\"expectations\":[")
            .contains("\"label\":\"body contains ok\"");
    }

    @Test
    void previewHydratesRequestWithoutExecutingHttp() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes/auth"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), """
            BASE_URL=https://seed.example.com
            TOKEN=seed-token
            """);
        Files.writeString(tempDir.resolve(".doppio/recipes/auth/login.dopo"), """
            @name Login user
            @var TOKEN=local-token
            POST {{BASE_URL}}/auth/login
            -h Authorization=Bearer {{TOKEN}}
            <form|
            email=user@example.com
            role=admin
            |>
            """);
        var out = new StringWriter();
        var transport = new FakeTransport();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of("BASE_URL", "https://env.example.com"),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            transport
        ).execute("preview", "auth/login");

        assertThat(exit).isZero();
        assertThat(transport.callCount).isZero();
        assertThat(out.toString())
            .contains("Doppio Preview")
            .contains("Name: Login user")
            .contains("POST")
            .contains("https://seed.example.com/auth/login")
            .contains("Authorization: Bearer local-token")
            .contains("Content-Type: application/x-www-form-urlencoded")
            .contains("email=user%40example.com&role=admin");
    }

    @Test
    void previewCanPrintJsonForAgents() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "BASE_URL=https://example.com");
        Files.writeString(tempDir.resolve(".doppio/recipes/text.dopo"), """
            @name Text ping
            @expect body contains hello
            POST {{BASE_URL}}/ping
            <text|
            hello
            |>
            """);
        var out = new StringWriter();
        var transport = new FakeTransport();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            transport
        ).execute("preview", "--json", "text");

        assertThat(exit).isZero();
        assertThat(transport.callCount).isZero();
        assertThat(out.toString())
            .contains("\"kind\":\"preview\"")
            .contains("\"success\":true")
            .contains("\"name\":\"Text ping\"")
            .contains("\"url\":\"https://example.com/ping\"")
            .contains("\"kind\":\"TEXT\"")
            .contains("\"contentType\":\"text/plain; charset=utf-8\"")
            .contains("\"content\":\"hello\"")
            .contains("\"expectations\":[")
            .contains("\"label\":\"body contains hello\"");
    }

    @Test
    void previewJsonIncludesSelectedEnvironment() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/seeds"));
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "BASE_URL=https://default.example.com");
        Files.writeString(tempDir.resolve(".doppio/seeds/dev.seed"), "BASE_URL=https://dev.example.com");
        Files.writeString(tempDir.resolve(".doppio/recipes/ping.dopo"), "GET {{BASE_URL}}/ping");
        var out = new StringWriter();
        var transport = new FakeTransport();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            transport
        ).execute("preview", "--json", "--env", "dev", "ping");

        assertThat(exit).isZero();
        assertThat(transport.callCount).isZero();
        assertThat(out.toString())
            .contains("\"environment\":\"dev\"")
            .contains("\"url\":\"https://dev.example.com/ping\"");
    }

    @Test
    void previewJsonErrorsDoNotExecuteHttp() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        Files.writeString(tempDir.resolve(".doppio/recipes/missing.dopo"), "GET https://example.com/{{MISSING}}");
        var out = new StringWriter();
        var err = new StringWriter();
        var transport = new FakeTransport();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(err, true),
            transport
        ).execute("preview", "--json", "missing");

        assertThat(exit).isEqualTo(1);
        assertThat(out.toString()).isBlank();
        assertThat(transport.callCount).isZero();
        assertThat(err.toString())
            .contains("\"success\":false")
            .contains("\"errorKind\":\"TEMPLATE\"")
            .contains("MISSING");
    }

    @Test
    void showResolvesOptionalDopoExtensionFromProjectRootAndDoppioDirectory() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes/auth"));
        Files.writeString(tempDir.resolve(".doppio/recipes/auth/login.dopo"), """
            @name Login user
            GET https://example.com/login
            """);

        var rootOut = new StringWriter();
        var rootExit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(rootOut, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("show", "auth/login");

        assertThat(rootExit).isZero();
        assertThat(rootOut.toString())
            .contains("Name: Login user")
            .contains("File: auth/login.dopo");

        var doppioOut = new StringWriter();
        var doppioExit = DoppioCommand.commandLine(
            tempDir.resolve(".doppio"),
            Map.of(),
            new PrintWriter(doppioOut, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("show", "auth/login");

        assertThat(doppioExit).isZero();
        assertThat(doppioOut.toString())
            .contains("Name: Login user")
            .contains("File: auth/login.dopo");
    }

    @Test
    void cleanRemovesSavedReportsUnderRequestsFolder() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes/auth"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "BASE_URL=https://example.com");
        Files.writeString(tempDir.resolve(".doppio/recipes/auth/login.dopo"), "GET {{BASE_URL}}/login");
        Files.writeString(tempDir.resolve(".doppio/recipes/auth/login-1700000000000.txt"), "old report");
        Files.writeString(tempDir.resolve(".doppio/recipes/auth/notes.txt"), "keep");
        var out = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("clean");

        assertThat(exit).isZero();
        assertThat(out.toString()).contains("Removed 1 saved report");
        assertThat(tempDir.resolve(".doppio/recipes/auth/login-1700000000000.txt")).doesNotExist();
        assertThat(tempDir.resolve(".doppio/recipes/auth/notes.txt")).exists();
    }

    @Test
    void rmMovesRequestFileToDoppioTrash() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes/auth"));
        var requestFile = tempDir.resolve(".doppio/recipes/auth/login.dopo");
        Files.writeString(requestFile, "GET https://example.com/login");
        var out = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("rm", "auth/login");

        assertThat(exit).isZero();
        assertThat(requestFile).doesNotExist();
        try (var trashFiles = Files.list(tempDir.resolve(".doppio/trash"))) {
            var files = trashFiles.toList();
            assertThat(files).hasSize(1);
            assertThat(files.getFirst().getFileName().toString()).endsWith("auth-login.dopo");
            assertThat(Files.readString(files.getFirst())).isEqualTo("GET https://example.com/login");
        }
        assertThat(out.toString())
            .contains("Moved recipe to trash")
            .contains("auth/login.dopo");
    }

    @Test
    void rmRejectsFilesOutsideRequestsFolder() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        var looseFile = tempDir.resolve(".doppio/loose.dopo");
        Files.writeString(looseFile, "GET https://example.com");
        var err = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir.resolve(".doppio"),
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(err, true),
            new FakeTransport()
        ).execute("rm", "loose.dopo");

        assertThat(exit).isEqualTo(1);
        assertThat(looseFile).exists();
        assertThat(err.toString()).contains("rm only removes files under .doppio/recipes");
    }

    @Test
    void listPrintsRequestTreeWithoutInternalRequestsFolder() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes/auth"));
        Files.writeString(tempDir.resolve(".doppio/recipes/auth/login.dopo"), """
            @name Login user
            POST https://example.com/login
            """);
        Files.writeString(tempDir.resolve(".doppio/recipes/test.dopo"), "GET https://example.com/test");
        Files.writeString(tempDir.resolve(".doppio/recipes/bad.dopo"), """
            @name Broken request
            FETCH nope
            """);
        var out = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("list");

        assertThat(exit).isZero();
        assertThat(out.toString())
            .contains("Recipes")
            .contains(tempDir.resolve(".doppio").toAbsolutePath().normalize().toString())
            .contains("`-- recipes/")
            .contains("auth/")
            .contains("Login user (auth/login.dopo)")
            .contains("test (test.dopo)")
            .contains("Broken request (bad.dopo) [parse error]")
            .doesNotContain(".doppio/recipes");
    }

    @Test
    void lsAliasPrintsRequestTreeLikeList() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes/auth"));
        Files.writeString(tempDir.resolve(".doppio/recipes/auth/login.dopo"), """
            @name Login user
            GET https://example.com/login
            """);
        var out = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("ls");

        assertThat(exit).isZero();
        assertThat(out.toString())
            .contains("Recipes")
            .contains(tempDir.resolve(".doppio").toAbsolutePath().normalize().toString())
            .contains("auth/")
            .contains("Login user (auth/login.dopo)");
    }

    @Test
    void lsAliasCanPrintJsonRequestIndex() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes/auth"));
        Files.writeString(tempDir.resolve(".doppio/recipes/auth/login.dopo"), """
            @name Login user
            GET https://example.com/login
            """);
        Files.writeString(tempDir.resolve(".doppio/recipes/bad.dopo"), "FETCH nope");
        var out = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("ls", "--json");

        assertThat(exit).isZero();
        assertThat(out.toString())
            .contains("\"kind\":\"list\"")
            .contains("\"project\":\"" + tempDir.resolve(".doppio").toAbsolutePath().normalize())
            .contains("\"name\":\"Login user\"")
            .contains("\"path\":\"auth/login.dopo\"")
            .contains("\"name\":\"bad\"")
            .contains("\"error\":\"parse error\"");
    }

    @Test
    void checkValidatesAllRequestsWithoutExecutingHttp() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes/auth"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "BASE_URL=https://example.com");
        Files.writeString(tempDir.resolve(".doppio/recipes/auth/login.dopo"), """
            @expect status=200
            GET {{BASE_URL}}/login
            """);
        Files.writeString(tempDir.resolve(".doppio/recipes/broken.dopo"), """
            POST {{BASE_URL}}/broken
            <json|
            {"name":}
            |>
            """);
        var out = new StringWriter();
        var transport = new FakeTransport();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            transport
        ).execute("check");

        assertThat(exit).isEqualTo(1);
        assertThat(transport.callCount).isZero();
        assertThat(out.toString())
            .contains("Doppio Check")
            .contains("Valid: 1")
            .contains("Failed: 1")
            .contains("valid   auth/login.dopo")
            .contains("failed  broken.dopo")
            .contains("expected JSON value");
    }

    @Test
    void checkCanTargetFolderAndFileWithOptionalExtension() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes/auth"));
        Files.writeString(tempDir.resolve(".doppio/recipes/auth/login.dopo"), "GET https://example.com/login");
        Files.writeString(tempDir.resolve(".doppio/recipes/auth/refresh.dopo"), "GET https://example.com/refresh");
        Files.writeString(tempDir.resolve(".doppio/recipes/other.dopo"), "GET https://example.com/other");
        var transport = new FakeTransport();
        var folderOut = new StringWriter();

        var folderExit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(folderOut, true),
            new PrintWriter(new StringWriter(), true),
            transport
        ).execute("check", "auth");

        assertThat(folderExit).isZero();
        assertThat(transport.callCount).isZero();
        assertThat(folderOut.toString())
            .contains("Valid: 2")
            .contains("auth/login.dopo")
            .contains("auth/refresh.dopo")
            .doesNotContain("other.dopo");

        var fileOut = new StringWriter();
        var fileExit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(fileOut, true),
            new PrintWriter(new StringWriter(), true),
            transport
        ).execute("check", "other");

        assertThat(fileExit).isZero();
        assertThat(transport.callCount).isZero();
        assertThat(fileOut.toString())
            .contains("Valid: 1")
            .contains("other.dopo");
    }

    @Test
    void checkCanUseSelectedEnvironment() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/seeds"));
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "BASE_URL=https://default.example.com");
        Files.writeString(tempDir.resolve(".doppio/seeds/dev.seed"), "BASE_URL=https://dev.example.com");
        Files.writeString(tempDir.resolve(".doppio/recipes/env.dopo"), "GET {{BASE_URL}}/health");
        var out = new StringWriter();
        var transport = new FakeTransport();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            transport
        ).execute("check", "--env", "dev");

        assertThat(exit).isZero();
        assertThat(transport.callCount).isZero();
        assertThat(out.toString())
            .contains("Seed: dev")
            .contains("Valid: 1")
            .contains("valid   env.dopo");
    }

    @Test
    void checkReportsInvalidExpectationWithoutExecutingHttp() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        Files.writeString(tempDir.resolve(".doppio/recipes/bad-expect.dopo"), """
            @expect header Content-Type equals json
            GET https://example.com
            """);
        var out = new StringWriter();
        var transport = new FakeTransport();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            transport
        ).execute("check", "bad-expect");

        assertThat(exit).isEqualTo(1);
        assertThat(transport.callCount).isZero();
        assertThat(out.toString())
            .contains("Failed: 1")
            .contains("bad-expect.dopo")
            .contains("@expect status=200");
    }

    @Test
    void doctorReportsHealthyProjectWithoutExecutingHttp() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "BASE_URL=https://example.com");
        Files.writeString(tempDir.resolve(".doppio/recipes/health.dopo"), "GET {{BASE_URL}}/health");
        var out = new StringWriter();
        var transport = new FakeTransport();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            transport
        ).execute("doctor");

        assertThat(exit).isZero();
        assertThat(transport.callCount).isZero();
        assertThat(out.toString())
            .contains("Doppio Doctor")
            .contains("Project: " + tempDir.resolve(".doppio").toAbsolutePath().normalize())
            .contains("PASS project")
            .contains("PASS seed")
            .contains("PASS recipes")
            .contains("PASS check")
            .contains("Fail: 0");
    }

    @Test
    void doctorReportsMissingProjectAndInvalidRequests() throws Exception {
        var missingOut = new StringWriter();

        var missingExit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(missingOut, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("doctor");

        assertThat(missingExit).isEqualTo(1);
        assertThat(missingOut.toString())
            .contains("Project: (not found)")
            .contains("FAIL project")
            .contains("No .doppio project found");

        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        Files.writeString(tempDir.resolve(".doppio/recipes/broken.dopo"), """
            POST https://example.com
            <json|
            {"name":}
            |>
            """);
        var invalidOut = new StringWriter();

        var invalidExit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(invalidOut, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("doctor");

        assertThat(invalidExit).isEqualTo(1);
        assertThat(invalidOut.toString())
            .contains("FAIL check")
            .contains("broken.dopo")
            .contains("expected JSON value");
    }

    @Test
    void doctorReportsSelectedEnvironment() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/seeds"));
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "BASE_URL=https://default.example.com");
        Files.writeString(tempDir.resolve(".doppio/seeds/dev.seed"), "BASE_URL=https://dev.example.com");
        Files.writeString(tempDir.resolve(".doppio/recipes/health.dopo"), "GET {{BASE_URL}}/health");
        var out = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("doctor", "--env", "dev");

        assertThat(exit).isZero();
        assertThat(out.toString())
            .contains("Seed: dev")
            .contains("PASS seed")
            .contains("1 seed overlay(s) found: dev")
            .contains("Selected seed found: dev")
            .contains("Fail: 0");

        var missingOut = new StringWriter();
        var missingExit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(missingOut, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("doctor", "--env", "missing");

        assertThat(missingExit).isEqualTo(1);
        assertThat(missingOut.toString())
            .contains("Seed: missing")
            .contains("FAIL seed")
            .contains("Selected seed not found: missing");
    }

    @Test
    void docsPrintsComprehensiveQuickReference() {
        var out = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("docs");

        assertThat(exit).isZero();
        assertThat(out.toString())
            .contains("Doppio Docs")
            .contains(".doppio/default.seed")
            .contains("doppio gen --env dev")
            .contains("doppio gen auth/login --method POST --body form --bearer")
            .contains("doppio gen auth/login --from-curl")
            .contains("doppio doctor --env dev")
            .contains("doppio ls --json")
            .contains("doppio format auth/login")
            .contains("JSON bodies are pretty-printed")
            .contains("doppio preview auth/login --env dev --json")
            .contains("doppio run auth/login --env dev --json")
            .contains("doppio ls");
    }

    @Test
    void formatWithoutTargetFormatsAllProjectRequests() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes/auth"));
        var login = tempDir.resolve(".doppio/recipes/auth/login.dopo");
        var ping = tempDir.resolve(".doppio/recipes/ping.dopo");
        Files.writeString(login, """
            @name   Login   user
            POST   https://example.com/auth/login
            header   Content-Type=application/json
            <json|
            {"password":"{{PASSWORD}}",
            # "username":"{{USERNAME}}",
            "remember":true}
            |>
            """);
        Files.writeString(ping, "GET https://example.com/ping\n");
        var out = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("format");

        assertThat(exit).isZero();
        assertThat(out.toString())
            .contains("Doppio Format")
            .contains("Changed: 1")
            .contains("Unchanged: 1")
            .contains("changed   auth/login.dopo")
            .contains("unchanged ping.dopo");
        assertThat(Files.readString(login)).isEqualTo("""
            @name Login user
            POST https://example.com/auth/login
            -h Content-Type=application/json

            <json|
            {
              "password": "{{PASSWORD}}",
              # "username":"{{USERNAME}}",
              "remember": true
            }
            |>
            """);
    }

    @Test
    void formatCanTargetFolderAndFileWithOptionalExtension() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes/auth"));
        var login = tempDir.resolve(".doppio/recipes/auth/login.dopo");
        var refresh = tempDir.resolve(".doppio/recipes/auth/refresh.dopo");
        var other = tempDir.resolve(".doppio/recipes/other.dopo");
        Files.writeString(login, "GET    https://example.com/login\n");
        Files.writeString(refresh, "GET    https://example.com/refresh\n");
        Files.writeString(other, "GET    https://example.com/other\n");

        var folderExit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("format", "auth");

        assertThat(folderExit).isZero();
        assertThat(Files.readString(login)).isEqualTo("GET https://example.com/login\n");
        assertThat(Files.readString(refresh)).isEqualTo("GET https://example.com/refresh\n");
        assertThat(Files.readString(other)).isEqualTo("GET    https://example.com/other\n");

        var fileExit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("format", "other");

        assertThat(fileExit).isZero();
        assertThat(Files.readString(other)).isEqualTo("GET https://example.com/other\n");
    }

    @Test
    void formatReportsInvalidJsonAndDoesNotRewriteFile() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        var broken = tempDir.resolve(".doppio/recipes/broken.dopo");
        var original = """
            POST https://example.com
            <json|
            {"username": "{{USERNAME}}" # inline comment}
            |>
            """;
        Files.writeString(broken, original);
        var out = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("format", "broken");

        assertThat(exit).isEqualTo(1);
        assertThat(Files.readString(broken)).isEqualTo(original);
        assertThat(out.toString())
            .contains("Failed: 1")
            .contains("failed")
            .contains("broken.dopo")
            .contains("Inline JSON comments");
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

    private void assertGeneratedBody(
        String path,
        String body,
        String expectedBlock,
        String expectedContent,
        String expectedContentType
    ) throws Exception {
        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("gen", path, "--body", body);

        assertThat(exit).isZero();
        assertThat(Files.readString(tempDir.resolve(".doppio/recipes/" + path + ".dopo")))
            .contains(expectedBlock)
            .contains(expectedContent)
            .contains(expectedContentType);
    }

    private void assertInvalidGen(String path, String expectedError, String... args) throws Exception {
        var err = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(err, true),
            new FakeTransport()
        ).execute(args);

        assertThat(exit).isEqualTo(1);
        assertThat(err.toString()).contains(expectedError);
        assertThat(tempDir.resolve(".doppio/recipes/" + path + ".dopo")).doesNotExist();
    }
}
