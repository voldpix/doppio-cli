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
        assertThat(tempDir.resolve(".doppio/local.seed")).doesNotExist();
        assertThat(tempDir.resolve(".doppio/requests/example.dopo")).exists();
        assertThat(tempDir.resolve(".doppio/requests/test.dopo")).exists();
        assertThat(tempDir.resolve("requests")).doesNotExist();
        assertThat(Files.readString(tempDir.resolve(".doppio/requests/test.dopo")))
            .contains("@name Smoke test")
            .contains("GET {{BASE_URL}}/get")
            .contains("{{TEST_ID}}");
        assertThat(out.toString())
            .contains("Initialized Doppio project")
            .contains(tempDir.resolve(".doppio").toAbsolutePath().normalize().toString())
            .contains("|-- default.seed")
            .contains("`-- requests/")
            .contains("|-- example.dopo")
            .contains("`-- test.dopo");

        Files.writeString(tempDir.resolve(".doppio/default.seed"), "TOKEN=keep-me");
        Files.writeString(tempDir.resolve(".doppio/requests/test.dopo"), "GET https://keep.example.com");
        DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("init");

        assertThat(Files.readString(tempDir.resolve(".doppio/default.seed"))).isEqualTo("TOKEN=keep-me");
        assertThat(Files.readString(tempDir.resolve(".doppio/requests/test.dopo"))).isEqualTo("GET https://keep.example.com");
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
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), """
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
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "BASE_URL=https://example.com");
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), """
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
    void runCanSaveReportNextToResolvedRequestFile() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), """
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
        var reports = Files.list(tempDir.resolve(".doppio/requests/auth"))
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
        Files.createDirectories(tempDir.resolve(".doppio/requests"));
        var out = new StringWriter();
        var err = new StringWriter();

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(out, true),
            new PrintWriter(err, true),
            new FakeTransport()
        ).execute("gen", "auth/login");

        var requestFile = tempDir.resolve(".doppio/requests/auth/login.dopo");
        assertThat(exit).isZero();
        assertThat(err.toString()).isBlank();
        assertThat(requestFile).exists();
        assertThat(Files.readString(requestFile))
            .contains("@name Login")
            .contains("POST {{BASE_URL}}/path")
            .contains("-h Content-Type=application/json")
            .contains("<json|");
        assertThat(out.toString())
            .contains("Created request")
            .contains("auth/login.dopo");
    }

    @Test
    void genMethodGetCreatesNoBodyByDefault() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests"));

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("gen", "users/me", "--method", "GET");

        assertThat(exit).isZero();
        var content = Files.readString(tempDir.resolve(".doppio/requests/users/me.dopo"));
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
        Files.createDirectories(tempDir.resolve(".doppio/requests"));

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("gen", "jobs/start", "--method", "POST", "--body", "none");

        assertThat(exit).isZero();
        var content = Files.readString(tempDir.resolve(".doppio/requests/jobs/start.dopo"));
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
        Files.createDirectories(tempDir.resolve(".doppio/requests"));

        assertGeneratedBody("json-request", "json", "<json|", "\"key\": \"value\"", "Content-Type=application/json");
        assertGeneratedBody("text-request", "text", "<text|", "hello", "Content-Type=text/plain; charset=utf-8");
        assertGeneratedBody("csv-request", "csv", "<csv|", "name,value", "Content-Type=text/csv; charset=utf-8");
        assertGeneratedBody("form-request", "form", "<form|", "key=value", "Content-Type=application/x-www-form-urlencoded");
    }

    @Test
    void genAddsBearerHeadersAndQueries() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests"));

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
        assertThat(Files.readString(tempDir.resolve(".doppio/requests/users/search.dopo")))
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
        Files.createDirectories(tempDir.resolve(".doppio/requests"));

        var exit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(new StringWriter(), true),
            new FakeTransport()
        ).execute("gen", "custom/content", "--body", "json", "-H", "Content-Type=application/custom");

        assertThat(exit).isZero();
        assertThat(Files.readString(tempDir.resolve(".doppio/requests/custom/content.dopo")))
            .contains("-h Content-Type=application/custom")
            .doesNotContain("-h Content-Type=application/json");
    }

    @Test
    void genRejectsInvalidHelperOptionsWithoutCreatingFiles() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests"));

        assertInvalidGen("bad-method", "Unsupported method", "gen", "bad-method", "--method", "FETCH");
        assertInvalidGen("bad-body", "Unsupported body kind", "gen", "bad-body", "--body", "xml");
        assertInvalidGen("bad-header", "Header must use key=value", "gen", "bad-header", "-H", "Authorization");
        assertInvalidGen("bad-query", "Query param key is missing", "gen", "bad-query", "-q", "=broken");
    }

    @Test
    void genRejectsOverwritesAndInternalRequestsPrefix() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests"));
        Files.writeString(tempDir.resolve(".doppio/requests/existing.dopo"), "GET https://example.com");
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
        assertThat(Files.readString(tempDir.resolve(".doppio/requests/existing.dopo")))
            .isEqualTo("GET https://example.com");

        err.getBuffer().setLength(0);
        var prefixedExit = DoppioCommand.commandLine(
            tempDir,
            Map.of(),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(err, true),
            new FakeTransport()
        ).execute("gen", "requests/auth/login");

        assertThat(prefixedExit).isEqualTo(1);
        assertThat(err.toString()).contains("without .doppio/requests");
    }

    @Test
    void showPrintsRequestDetailsWithoutExecutingHttp() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), """
            @name Login user
            @var EMAIL=user@example.com
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
            .contains("EMAIL=user@example.com");
    }

    @Test
    void showCanPrintJsonInspectionWithoutExecutingHttp() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), """
            @name Login user
            @var EMAIL=user@example.com
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
            .contains("\"EMAIL\":\"user@example.com\"");
    }

    @Test
    void previewHydratesRequestWithoutExecutingHttp() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), """
            BASE_URL=https://seed.example.com
            TOKEN=seed-token
            """);
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), """
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
        Files.createDirectories(tempDir.resolve(".doppio/requests"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "BASE_URL=https://example.com");
        Files.writeString(tempDir.resolve(".doppio/requests/text.dopo"), """
            @name Text ping
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
            .contains("\"content\":\"hello\"");
    }

    @Test
    void previewJsonErrorsDoNotExecuteHttp() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests"));
        Files.writeString(tempDir.resolve(".doppio/requests/missing.dopo"), "GET https://example.com/{{MISSING}}");
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
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), """
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
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "BASE_URL=https://example.com");
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), "GET {{BASE_URL}}/login");
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login-1700000000000.txt"), "old report");
        Files.writeString(tempDir.resolve(".doppio/requests/auth/notes.txt"), "keep");
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
        assertThat(tempDir.resolve(".doppio/requests/auth/login-1700000000000.txt")).doesNotExist();
        assertThat(tempDir.resolve(".doppio/requests/auth/notes.txt")).exists();
    }

    @Test
    void rmMovesRequestFileToDoppioTrash() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        var requestFile = tempDir.resolve(".doppio/requests/auth/login.dopo");
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
            .contains("Moved request to trash")
            .contains("auth/login.dopo");
    }

    @Test
    void rmRejectsFilesOutsideRequestsFolder() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests"));
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
        assertThat(err.toString()).contains("rm only removes files under .doppio/requests");
    }

    @Test
    void listPrintsRequestTreeWithoutInternalRequestsFolder() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), """
            @name Login user
            POST https://example.com/login
            """);
        Files.writeString(tempDir.resolve(".doppio/requests/test.dopo"), "GET https://example.com/test");
        Files.writeString(tempDir.resolve(".doppio/requests/bad.dopo"), """
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
            .contains("Requests")
            .contains(tempDir.resolve(".doppio").toAbsolutePath().normalize().toString())
            .contains("`-- requests/")
            .contains("auth/")
            .contains("Login user (auth/login.dopo)")
            .contains("test (test.dopo)")
            .contains("Broken request (bad.dopo) [parse error]")
            .doesNotContain(".doppio/requests");
    }

    @Test
    void lsAliasPrintsRequestTreeLikeList() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), """
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
            .contains("Requests")
            .contains(tempDir.resolve(".doppio").toAbsolutePath().normalize().toString())
            .contains("auth/")
            .contains("Login user (auth/login.dopo)");
    }

    @Test
    void lsAliasCanPrintJsonRequestIndex() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        Files.writeString(tempDir.resolve(".doppio/requests/auth/login.dopo"), """
            @name Login user
            GET https://example.com/login
            """);
        Files.writeString(tempDir.resolve(".doppio/requests/bad.dopo"), "FETCH nope");
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
            .contains("doppio gen auth/login --method POST --body form --bearer")
            .contains("doppio ls --json")
            .contains("doppio format auth/login")
            .contains("JSON bodies are pretty-printed")
            .contains("doppio preview auth/login --json")
            .contains("doppio run auth/login --json")
            .contains("doppio ls");
    }

    @Test
    void formatWithoutTargetFormatsAllProjectRequests() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        var login = tempDir.resolve(".doppio/requests/auth/login.dopo");
        var ping = tempDir.resolve(".doppio/requests/ping.dopo");
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
        Files.createDirectories(tempDir.resolve(".doppio/requests/auth"));
        var login = tempDir.resolve(".doppio/requests/auth/login.dopo");
        var refresh = tempDir.resolve(".doppio/requests/auth/refresh.dopo");
        var other = tempDir.resolve(".doppio/requests/other.dopo");
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
        Files.createDirectories(tempDir.resolve(".doppio/requests"));
        var broken = tempDir.resolve(".doppio/requests/broken.dopo");
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
        assertThat(Files.readString(tempDir.resolve(".doppio/requests/" + path + ".dopo")))
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
        assertThat(tempDir.resolve(".doppio/requests/" + path + ".dopo")).doesNotExist();
    }
}
