package dev.voldpix.doppio.shell;

import dev.voldpix.doppio.cli.DoppioCommand;
import dev.voldpix.doppio.console.ConsoleFormatter;
import dev.voldpix.doppio.env.DoppioEnvironment;
import dev.voldpix.doppio.http.HttpTransport;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;
import dev.voldpix.doppio.model.RunReport;
import dev.voldpix.doppio.pipeline.DoppioPipeline;
import dev.voldpix.doppio.pipeline.DoppioProjectResolver;
import dev.voldpix.doppio.report.RunReportWriter;
import dev.voldpix.doppio.request.RequestFileCreator;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DoppioShell {
    private static final String NO_PROJECT_MESSAGE = "No Doppio project found. Navigate to a Doppio project or run doppio init first.";
    private static final int BODY_PREVIEW_LIMIT = 600;

    private final Path initialWorkingDirectory;
    private final Map<String, String> environment;
    private final DoppioPipeline pipeline;
    private final HttpTransport transport;
    private final DoppioStatusStore statusStore;
    private final DoppioProjectResolver projectResolver;
    private final ShellRequestResolver requestResolver;
    private final ShellCommandParser commandParser;
    private final ConsoleFormatter formatter;
    private final RunReportWriter reportWriter;
    private final RequestFileCreator requestFileCreator;
    private final ExternalEditor editor;
    private final PrintWriter out;
    private final PrintWriter err;
    private ShellSession session;

    public DoppioShell(
        Path initialWorkingDirectory,
        Map<String, String> environment,
        DoppioPipeline pipeline,
        HttpTransport transport,
        DoppioStatusStore statusStore,
        PrintWriter out,
        PrintWriter err
    ) {
        this(initialWorkingDirectory,
            environment,
            pipeline,
            transport,
            statusStore,
            new DoppioProjectResolver(),
            new ShellRequestResolver(),
            new ShellCommandParser(),
            new ConsoleFormatter(),
            new RunReportWriter(),
            new RequestFileCreator(),
            new ExternalEditor(),
            out,
            err);
    }

    public DoppioShell(
        Path initialWorkingDirectory,
        Map<String, String> environment,
        DoppioPipeline pipeline,
        HttpTransport transport,
        DoppioStatusStore statusStore,
        DoppioProjectResolver projectResolver,
        ShellRequestResolver requestResolver,
        ShellCommandParser commandParser,
        ConsoleFormatter formatter,
        RunReportWriter reportWriter,
        RequestFileCreator requestFileCreator,
        ExternalEditor editor,
        PrintWriter out,
        PrintWriter err
    ) {
        this.initialWorkingDirectory = initialWorkingDirectory.toAbsolutePath().normalize();
        this.environment = Map.copyOf(environment);
        this.pipeline = pipeline;
        this.transport = transport;
        this.statusStore = statusStore;
        this.projectResolver = projectResolver;
        this.requestResolver = requestResolver;
        this.commandParser = commandParser;
        this.formatter = formatter;
        this.reportWriter = reportWriter;
        this.requestFileCreator = requestFileCreator;
        this.editor = editor;
        this.out = out;
        this.err = err;
    }

    public ShellSession session() {
        return session;
    }

    public int run(LineReader reader, Path projectOption, String envName) {
        try {
            session = openSession(reader, projectOption, envName);
            statusStore.recordProject(session.projectRoot());
            printProjectBanner();
            while (true) {
                String line;
                try {
                    line = reader.readLine(prompt());
                } catch (UserInterruptException e) {
                    out.println();
                    continue;
                } catch (EndOfFileException e) {
                    out.println();
                    return 0;
                }

                if (!handle(line, reader)) {
                    return 0;
                }
            }
        } catch (DoppioException e) {
            err.println(e.getMessage());
            err.flush();
            return 1;
        }
    }

    private ShellSession openSession(LineReader reader, Path projectOption, String envName) throws DoppioException {
        var selectedEnvironment = DoppioEnvironment.of(envName);
        var start = projectOption == null ? initialWorkingDirectory : projectOption.toAbsolutePath().normalize();
        var doppioDir = projectResolver.findDoppioDirectory(start);
        if (doppioDir != null) {
            return new ShellSession(projectRoot(doppioDir), doppioDir, selectedEnvironment.name());
        }

        if (projectOption != null) {
            throw new DoppioException(ErrorKind.FILE, NO_PROJECT_MESSAGE);
        }

        var recent = statusStore.validRecentProjects();
        if (recent.isEmpty()) {
            throw new DoppioException(ErrorKind.FILE, NO_PROJECT_MESSAGE);
        }
        out.println("Recent Doppio projects:");
        for (var i = 0; i < recent.size(); i++) {
            out.printf("  %d. %s%n", i + 1, recent.get(i).path());
        }
        var choice = readPickerLine(reader, "Choose project: ", "No project selected.");
        var selected = parseProjectChoice(choice, recent)
            .orElseThrow(() -> new DoppioException(ErrorKind.FILE, "Invalid project choice: " + choice));
        var selectedDoppio = projectResolver.findDoppioDirectory(selected.path());
        if (selectedDoppio == null) {
            throw new DoppioException(ErrorKind.FILE, NO_PROJECT_MESSAGE);
        }
        return new ShellSession(projectRoot(selectedDoppio), selectedDoppio, selectedEnvironment.name());
    }

    private boolean handle(String line, LineReader reader) {
        try {
            var tokens = commandParser.parse(line);
            if (tokens.isEmpty()) {
                return true;
            }

            var command = tokens.getFirst();
            var args = tokens.subList(1, tokens.size());
            switch (command) {
                case "exit", "quit" -> {
                    return false;
                }
                case "help" -> printHelp();
                case "ls", "list" -> executeCli(List.of("list"));
                case "run" -> runRequest(args, reader);
                case "preview" -> previewRequest(args, reader);
                case "show" -> showRequest(args, reader);
                case "edit" -> editRequest(args, reader);
                case "gen" -> executeCli(join("gen", args));
                case "format" -> executeCli(join("format", args));
                case "check" -> executeCli(withSessionEnv("check", args));
                case "doctor" -> executeCli(withSessionEnv("doctor", args));
                case "clean" -> executeCli(List.of("clean"));
                case "rm" -> removeRequest(args, reader);
                case "body" -> printLastBody();
                case "save" -> saveLastReport();
                case "seed" -> handleSeed(args);
                case "env" -> handleEnv(args);
                case "projects" -> printProjects();
                case "project" -> switchProject(args);
                default -> out.println("Unknown shell command: " + command + ". Type `help`.");
            }
        } catch (DoppioException e) {
            formatter.printError(e, err);
        }
        return true;
    }

    private void runRequest(List<String> args, LineReader reader) throws DoppioException {
        var options = ShellCommandOptions.parse(args);
        var request = chooseRequest(singleTarget(options.args()), reader);
        if (request.isEmpty()) {
            return;
        }

        var report = pipeline.run(
            request.get().relativePath(),
            session.projectRoot(),
            environment,
            DoppioEnvironment.of(effectiveEnv(options.envName()))
        );
        session.lastRunReport(report);
        printCompactRun(report, effectiveEnv(options.envName()));
    }

    private void previewRequest(List<String> args, LineReader reader) throws DoppioException {
        var options = ShellCommandOptions.parse(args);
        var request = chooseRequest(singleTarget(options.args()), reader);
        if (request.isEmpty()) {
            return;
        }

        var report = pipeline.preview(
            request.get().relativePath(),
            session.projectRoot(),
            environment,
            DoppioEnvironment.of(effectiveEnv(options.envName()))
        );
        formatter.printPreview(report, out);
    }

    private void showRequest(List<String> args, LineReader reader) throws DoppioException {
        var request = chooseRequest(singleTarget(args), reader);
        request.ifPresent(candidate -> executeCli(List.of("show", candidate.relativePath().toString())));
    }

    private void editRequest(List<String> args, LineReader reader) throws DoppioException {
        var request = chooseRequest(singleTarget(args), reader);
        if (request.isEmpty()) {
            return;
        }
        editor.open(session.doppioDirectory().resolve("requests").resolve(request.get().relativePath()), environment);
    }

    private void removeRequest(List<String> args, LineReader reader) throws DoppioException {
        var request = chooseRequest(singleTarget(args), reader);
        request.ifPresent(candidate -> executeCli(List.of("rm", candidate.relativePath().toString())));
    }

    private Optional<ShellRequestCandidate> chooseRequest(String target, LineReader reader) throws DoppioException {
        var lookup = requestResolver.resolve(target, session.projectRoot());
        if (!lookup.found()) {
            out.println("Request not found. Type `ls` to list requests.");
            return Optional.empty();
        }
        if (!lookup.ambiguous() && target != null && !target.isBlank()) {
            return Optional.of(lookup.only());
        }

        out.println(target == null || target.isBlank() ? "Choose request:" : "Multiple requests matched:");
        for (var i = 0; i < lookup.matches().size(); i++) {
            var candidate = lookup.matches().get(i);
            out.printf("  %d. %s (%s)%n", i + 1, candidate.displayName(), candidate.relativePath());
        }
        var choice = readPickerLine(reader, "Request number: ", "No request selected.");
        var index = parsePositiveInt(choice);
        if (index < 1 || index > lookup.matches().size()) {
            out.println("Invalid request choice: " + choice);
            return Optional.empty();
        }
        return Optional.of(lookup.matches().get(index - 1));
    }

    private String readPickerLine(LineReader reader, String prompt, String emptyMessage) throws DoppioException {
        try {
            return reader.readLine(prompt);
        } catch (UserInterruptException | EndOfFileException e) {
            throw new DoppioException(ErrorKind.FILE, emptyMessage);
        }
    }

    private void handleSeed(List<String> args) throws DoppioException {
        if (args.isEmpty()) {
            out.println("Usage: seed list | seed edit default|NAME | seed gen NAME | seed rm NAME");
            return;
        }
        switch (args.getFirst()) {
            case "list" -> listSeeds();
            case "edit" -> {
                var name = requiredArg(args, "seed edit requires default or env name");
                editor.open(seedPath(name), environment);
            }
            case "gen" -> {
                var name = requiredArg(args, "seed gen requires an env name");
                var created = requestFileCreator.createEnvironment(name, session.projectRoot());
                out.println("Created seed: " + created.relativePath());
            }
            case "rm" -> removeSeed(requiredArg(args, "seed rm requires an env name"));
            default -> out.println("Unknown seed command: " + args.getFirst());
        }
    }

    private void handleEnv(List<String> args) throws DoppioException {
        if (args.isEmpty()) {
            out.println("Usage: env list | env use NAME | env clear | env edit NAME | env gen NAME");
            return;
        }
        switch (args.getFirst()) {
            case "list" -> listSeeds();
            case "use" -> {
                var name = requiredArg(args, "env use requires an env name");
                var path = seedPath(name);
                if (!Files.isRegularFile(path)) {
                    throw new DoppioException(ErrorKind.SEED, "Seed not found: " + name + ". Use `env gen " + name + "` first.");
                }
                session.environmentName(name);
                out.println("Using env: " + name);
            }
            case "clear" -> {
                session.environmentName(null);
                out.println("Using env: default");
            }
            case "edit" -> {
                var name = requiredArg(args, "env edit requires an env name");
                editor.open(seedPath(name), environment);
            }
            case "gen" -> {
                var name = requiredArg(args, "env gen requires an env name");
                var created = requestFileCreator.createEnvironment(name, session.projectRoot());
                out.println("Created env: " + created.relativePath());
            }
            default -> out.println("Unknown env command: " + args.getFirst());
        }
    }

    private void listSeeds() throws DoppioException {
        out.println("Seeds");
        var defaultSeed = session.doppioDirectory().resolve("default.seed");
        out.println("  default " + (Files.isRegularFile(defaultSeed) ? defaultSeed : "(missing)"));
        var envs = envNames();
        for (var env : envs) {
            out.println("  " + env + " " + session.doppioDirectory().resolve("envs").resolve(env + ".seed"));
        }
    }

    private List<String> envNames() throws DoppioException {
        var envsDir = session.doppioDirectory().resolve("envs");
        if (!Files.isDirectory(envsDir)) {
            return List.of();
        }
        try (var files = Files.walk(envsDir, 1)) {
            return files
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".seed"))
                .map(name -> name.substring(0, name.length() - ".seed".length()))
                .sorted()
                .toList();
        } catch (java.io.IOException e) {
            throw new DoppioException(ErrorKind.SEED, "Unable to list seeds", e);
        }
    }

    private Path seedPath(String name) throws DoppioException {
        if ("default".equals(name)) {
            return session.doppioDirectory().resolve("default.seed");
        }
        var env = DoppioEnvironment.of(name);
        var path = session.doppioDirectory().resolve("envs").resolve(env.fileName()).normalize();
        if (!path.startsWith(session.doppioDirectory().resolve("envs").normalize())) {
            throw new DoppioException(ErrorKind.SEED, "Seed path must stay inside .doppio/envs");
        }
        return path;
    }

    private void removeSeed(String name) throws DoppioException {
        if ("default".equals(name)) {
            throw new DoppioException(ErrorKind.SEED, "default.seed cannot be removed");
        }
        var path = seedPath(name);
        if (!Files.isRegularFile(path)) {
            throw new DoppioException(ErrorKind.SEED, "Seed not found: " + name);
        }
        var trash = session.doppioDirectory().resolve("trash/seeds");
        var target = trash.resolve(Instant.now().toEpochMilli() + "-" + path.getFileName());
        try {
            Files.createDirectories(trash);
            Files.move(path, target);
            out.println("Moved seed to trash: " + target);
        } catch (java.io.IOException e) {
            throw new DoppioException(ErrorKind.SEED, "Unable to remove seed: " + name, e);
        }
    }

    private void printLastBody() {
        var report = session.lastRunReport();
        if (report == null) {
            out.println("No run response yet.");
            return;
        }
        var body = report.response().body();
        out.println(body == null || body.isBlank() ? "(empty)" : body);
    }

    private void saveLastReport() throws DoppioException {
        var report = session.lastRunReport();
        if (report == null) {
            out.println("No run response yet.");
            return;
        }
        var saved = reportWriter.write(report);
        formatter.printSavedReport(saved, out);
    }

    private void printProjects() {
        var recent = statusStore.validRecentProjects();
        if (recent.isEmpty()) {
            out.println("No recent Doppio projects.");
            return;
        }
        for (var i = 0; i < recent.size(); i++) {
            out.printf("  %d. %s%n", i + 1, recent.get(i).path());
        }
    }

    private void switchProject(List<String> args) throws DoppioException {
        if (args.isEmpty()) {
            printProjects();
            return;
        }
        Path root;
        var recent = statusStore.validRecentProjects();
        var index = parsePositiveInt(args.getFirst());
        if (index > 0) {
            if (index > recent.size()) {
                throw new DoppioException(ErrorKind.FILE, "Invalid project choice: " + args.getFirst());
            }
            root = recent.get(index - 1).path();
        } else {
            root = Path.of(args.getFirst()).toAbsolutePath().normalize();
        }
        var doppioDir = projectResolver.findDoppioDirectory(root);
        if (doppioDir == null) {
            throw new DoppioException(ErrorKind.FILE, NO_PROJECT_MESSAGE);
        }
        session.switchProject(projectRoot(doppioDir), doppioDir);
        statusStore.recordProject(session.projectRoot());
        printProjectBanner();
    }

    private void printCompactRun(RunReport report, String envName) {
        var request = report.request();
        var response = report.response();
        var label = report.isSuccess() ? "SUCCESS" : "FAILED";
        out.println();
        out.println("Doppio Run");
        if (request.name() != null && !request.name().isBlank()) {
            out.println("Name: " + request.name());
        }
        out.println("Env: " + (envName == null || envName.isBlank() ? "default" : envName));
        out.println("Request: " + request.method() + " " + request.uri());
        out.println("Result: " + label + " " + response.statusCode() + "  " + response.duration().toMillis() + "ms");
        if (!report.expectations().isEmpty()) {
            out.println("Expectations: passed " + report.expectations().passedCount() + ", failed " + report.expectations().failedCount());
        }
        out.println("Body: " + preview(response.body()));
        out.println("Use `body` for the full response body or `save` for the full report.");
        out.flush();
    }

    private String preview(String body) {
        if (body == null || body.isBlank()) {
            return "(empty)";
        }
        var compact = body.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (compact.length() <= BODY_PREVIEW_LIMIT) {
            return compact;
        }
        return compact.substring(0, BODY_PREVIEW_LIMIT) + "...";
    }

    private void printHelp() {
        out.println("""
            Doppio shell commands
              ls | list
              gen <request> [options]
              edit [request]
              show [request]
              preview [request] [--env NAME]
              run [request] [--env NAME]
              body
              save
              format [request-or-folder]
              check [request-or-folder] [--env NAME]
              rm [request]
              seed list | seed edit default|NAME | seed gen NAME | seed rm NAME
              env list | env use NAME | env clear | env edit NAME | env gen NAME
              projects | project <number|path>
              help | exit
            """);
        out.flush();
    }

    private void executeCli(List<String> args) {
        DoppioCommand.commandLine(session.projectRoot(), environment, out, err, transport)
            .execute(args.toArray(String[]::new));
    }

    private List<String> withSessionEnv(String command, List<String> args) {
        var result = new ArrayList<String>();
        result.add(command);
        result.addAll(args);
        if (session.environmentName() != null && args.stream().noneMatch(arg -> arg.equals("--env") || arg.startsWith("--env="))) {
            result.add("--env");
            result.add(session.environmentName());
        }
        return result;
    }

    private List<String> join(String command, List<String> args) {
        var result = new ArrayList<String>();
        result.add(command);
        result.addAll(args);
        return result;
    }

    private String singleTarget(List<String> args) throws DoppioException {
        if (args.isEmpty()) {
            return null;
        }
        if (args.size() > 1) {
            throw new DoppioException(ErrorKind.FILE, "Expected one request target");
        }
        return args.getFirst();
    }

    private String requiredArg(List<String> args, String message) throws DoppioException {
        if (args.size() < 2 || args.get(1).isBlank()) {
            throw new DoppioException(ErrorKind.FILE, message);
        }
        return args.get(1);
    }

    private String effectiveEnv(String oneOffEnv) {
        return oneOffEnv == null ? session.environmentName() : oneOffEnv;
    }

    private String prompt() {
        return "doppio:[" + session.promptEnvironment() + "]> ";
    }

    private void printProjectBanner() {
        out.println("Doppio project: " + session.doppioDirectory());
        out.println("Type `help` for commands.");
        out.flush();
    }

    private Optional<RecentProject> parseProjectChoice(String choice, List<RecentProject> projects) {
        var index = parsePositiveInt(choice);
        if (index < 1 || index > projects.size()) {
            return Optional.empty();
        }
        return Optional.of(projects.get(index - 1));
    }

    private int parsePositiveInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private Path projectRoot(Path doppioDir) {
        return ".doppio".equals(doppioDir.getFileName().toString()) && doppioDir.getParent() != null
            ? doppioDir.getParent().toAbsolutePath().normalize()
            : doppioDir.toAbsolutePath().normalize();
    }
}
