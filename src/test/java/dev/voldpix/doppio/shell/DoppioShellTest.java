package dev.voldpix.doppio.shell;

import dev.voldpix.doppio.body.BodyProcessor;
import dev.voldpix.doppio.console.ConsoleFormatter;
import dev.voldpix.doppio.dsl.DslProcessor;
import dev.voldpix.doppio.http.HttpTransport;
import dev.voldpix.doppio.http.RequestPreparer;
import dev.voldpix.doppio.http.TransportException;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.DoppioResponse;
import dev.voldpix.doppio.model.PreparedRequest;
import dev.voldpix.doppio.pipeline.DoppioPipeline;
import dev.voldpix.doppio.pipeline.RequestFileResolver;
import dev.voldpix.doppio.report.RunReportWriter;
import dev.voldpix.doppio.request.RequestFileCreator;
import dev.voldpix.doppio.seed.SeedFileLoader;
import dev.voldpix.doppio.template.TemplateEngine;
import org.jline.reader.LineReader;
import org.jline.reader.EndOfFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DoppioShellTest {
    @TempDir
    Path tempDir;

    @Test
    void outsideProjectWithoutRecentProjectsFailsClearly() throws Exception {
        var out = new StringWriter();
        var err = new StringWriter();
        var shell = shell(tempDir.resolve("outside"), out, err, new FakeTransport(), new CapturingEditor());

        var exit = shell.run(reader(""), null, null);

        assertThat(exit).isEqualTo(1);
        assertThat(err.toString()).contains("No Doppio project found. Navigate to a Doppio project or run doppio init first.");
    }

    @Test
    void outsideProjectCanOpenRecentProjectPicker() throws Exception {
        var project = project("recent");
        var store = new DoppioStatusStore(tempDir.resolve("config"));
        store.recordProject(project);
        var out = new StringWriter();
        var err = new StringWriter();
        var shell = shell(tempDir.resolve("outside"), store, out, err, new FakeTransport(), new CapturingEditor());

        var exit = shell.run(reader("1\nexit\n"), null, null);

        assertThat(exit).isZero();
        assertThat(out.toString())
            .contains("Recent Doppio projects")
            .contains("Doppio project: " + project.resolve(".doppio").toAbsolutePath().normalize());
    }

    @Test
    void runResolvesNestedRequestByStem() throws Exception {
        var project = project("api");
        Files.writeString(project.resolve(".doppio/default.seed"), "BASE_URL=https://example.com");
        request(project, "auth/login.dopo", "@name Login\nGET {{BASE_URL}}/login");
        var out = new StringWriter();
        var err = new StringWriter();
        var transport = new FakeTransport();
        var shell = shell(project, out, err, transport, new CapturingEditor());

        var exit = shell.run(reader("run login\nexit\n"), null, null);

        assertThat(exit).isZero();
        assertThat(transport.lastRequest.uri().toString()).isEqualTo("https://example.com/login");
        assertThat(out.toString())
            .contains("Doppio project:")
            .contains("Env: default")
            .contains("Result: SUCCESS 200");
    }

    @Test
    void runWithoutTargetUsesPicker() throws Exception {
        var project = project("api");
        request(project, "auth/login.dopo", "@name Login\nGET https://example.com/login");
        request(project, "users/me.dopo", "@name Me\nGET https://example.com/me");
        var transport = new FakeTransport();
        var shell = shell(project, new StringWriter(), new StringWriter(), transport, new CapturingEditor());

        var exit = shell.run(reader("run\n2\nexit\n"), null, null);

        assertThat(exit).isZero();
        assertThat(transport.lastRequest.uri().toString()).isEqualTo("https://example.com/me");
    }

    @Test
    void seedEditResolvesDefaultAndEnvSeedFiles() throws Exception {
        var project = project("api");
        Files.createDirectories(project.resolve(".doppio/envs"));
        Files.writeString(project.resolve(".doppio/envs/dev.seed"), "BASE_URL=https://dev.example.com");
        var editor = new CapturingEditor();
        var shell = shell(project, new StringWriter(), new StringWriter(), new FakeTransport(), editor);

        var exit = shell.run(reader("seed edit default\nseed edit dev\nexit\n"), null, null);

        assertThat(exit).isZero();
        assertThat(editor.opened)
            .containsExactly(
                project.resolve(".doppio/default.seed").toAbsolutePath().normalize(),
                project.resolve(".doppio/envs/dev.seed").toAbsolutePath().normalize()
            );
    }

    @Test
    void promptDisplaysDefaultWhenNoEnvSelected() {
        var session = new ShellSession(tempDir, tempDir.resolve(".doppio"), null);

        assertThat(session.promptEnvironment()).isEqualTo("default");
    }

    private Path project(String name) throws Exception {
        var project = tempDir.resolve(name);
        Files.createDirectories(project.resolve(".doppio/requests"));
        Files.writeString(project.resolve(".doppio/default.seed"), "");
        return project;
    }

    private void request(Path project, String relativePath, String content) throws Exception {
        var path = project.resolve(".doppio/requests").resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private DoppioShell shell(Path workingDirectory, StringWriter out, StringWriter err, HttpTransport transport, ExternalEditor editor) {
        return shell(workingDirectory, new DoppioStatusStore(tempDir.resolve("config")), out, err, transport, editor);
    }

    private DoppioShell shell(
        Path workingDirectory,
        DoppioStatusStore statusStore,
        StringWriter out,
        StringWriter err,
        HttpTransport transport,
        ExternalEditor editor
    ) {
        return new DoppioShell(
            workingDirectory,
            Map.of(),
            pipeline(transport),
            transport,
            statusStore,
            new dev.voldpix.doppio.pipeline.DoppioProjectResolver(),
            new ShellRequestResolver(),
            new ShellCommandParser(),
            new ConsoleFormatter(false),
            new RunReportWriter(new ConsoleFormatter(false), java.time.Clock.systemUTC()),
            new RequestFileCreator(),
            editor,
            new PrintWriter(out, true),
            new PrintWriter(err, true)
        );
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

    private LineReader reader(String input) {
        var lines = new ArrayDeque<String>(Arrays.asList(input.split("\\R", -1)));
        return (LineReader) Proxy.newProxyInstance(
            LineReader.class.getClassLoader(),
            new Class<?>[] {LineReader.class},
            (proxy, method, args) -> {
                if (method.getName().startsWith("readLine")) {
                    if (lines.isEmpty()) {
                        throw new EndOfFileException();
                    }
                    return lines.removeFirst();
                }
                if (method.getReturnType().equals(boolean.class)) {
                    return false;
                }
                if (method.getReturnType().equals(int.class)) {
                    return 0;
                }
                if (method.getReturnType().equals(void.class)) {
                    return null;
                }
                return null;
            }
        );
    }

    private static class FakeTransport implements HttpTransport {
        private PreparedRequest lastRequest;

        @Override
        public DoppioResponse execute(PreparedRequest request, Duration timeout) throws TransportException {
            this.lastRequest = request;
            return new DoppioResponse(200, Map.of(), "ok", Duration.ofMillis(4));
        }
    }

    private static class CapturingEditor extends ExternalEditor {
        private final List<Path> opened = new ArrayList<>();

        @Override
        public void open(Path file, Map<String, String> environment) throws DoppioException {
            opened.add(file.toAbsolutePath().normalize());
        }
    }
}
