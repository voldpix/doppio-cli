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
    private final DoppioConfigStore configStore;
    private final DoppioProjectResolver projectResolver;
    private final ShellRequestResolver requestResolver;
    private final ShellCommandParser commandParser;
    private final ConsoleFormatter formatter;
    private final RunReportWriter reportWriter;
    private final RequestFileCreator requestFileCreator;
    private final ExternalEditor editor;
    private final ShellStyler styler;
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
            new DoppioConfigStore(statusStore.configDirectory()),
            new DoppioProjectResolver(),
            new ShellRequestResolver(),
            new ShellCommandParser(),
            new ConsoleFormatter(),
            new RunReportWriter(),
            new RequestFileCreator(),
            new ExternalEditor(),
            new ShellStyler(environment),
            out,
            err);
    }

    public DoppioShell(
        Path initialWorkingDirectory,
        Map<String, String> environment,
        DoppioPipeline pipeline,
        HttpTransport transport,
        DoppioStatusStore statusStore,
        DoppioConfigStore configStore,
        DoppioProjectResolver projectResolver,
        ShellRequestResolver requestResolver,
        ShellCommandParser commandParser,
        ConsoleFormatter formatter,
        RunReportWriter reportWriter,
        RequestFileCreator requestFileCreator,
        ExternalEditor editor,
        ShellStyler styler,
        PrintWriter out,
        PrintWriter err
    ) {
        this.initialWorkingDirectory = initialWorkingDirectory.toAbsolutePath().normalize();
        this.environment = Map.copyOf(environment);
        this.pipeline = pipeline;
        this.transport = transport;
        this.statusStore = statusStore;
        this.configStore = configStore;
        this.projectResolver = projectResolver;
        this.requestResolver = requestResolver;
        this.commandParser = commandParser;
        this.formatter = formatter;
        this.reportWriter = reportWriter;
        this.requestFileCreator = requestFileCreator;
        this.editor = editor;
        this.styler = styler;
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
            out.printf("  %s %s%n", styler.muted((i + 1) + "."), styler.path(recent.get(i).path()));
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
                case "ls", "list" -> executeCli(join("list", args));
                case "run" -> runRequest(args, reader);
                case "preview" -> previewRequest(args, reader);
                case "show" -> showRequest(args, reader);
                case "edit" -> editRequest(args, reader);
                case "gen" -> executeCli(join("gen", args));
                case "format" -> executeCli(join("format", args));
                case "check" -> executeCli(withSessionEnv("check", args));
                case "doctor" -> executeCli(withSessionEnv("doctor", args));
                case "clean" -> executeCli(join("clean", args));
                case "rm" -> removeRequest(args, reader);
                case "body" -> {
                    ensureNoArgs(args, "Usage: body");
                    printLastBody();
                }
                case "save" -> {
                    ensureNoArgs(args, "Usage: save");
                    saveLastReport();
                }
                case "seed" -> handleSeed(args);
                case "config" -> handleConfig(args);
                case "projects" -> {
                    ensureNoArgs(args, "Usage: projects");
                    printProjects();
                }
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
        openEditor(session.doppioDirectory().resolve("recipes").resolve(request.get().relativePath()));
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
            out.printf("  %s %s %s%n",
                styler.muted((i + 1) + "."),
                styler.header(candidate.displayName()),
                styler.muted("(" + candidate.relativePath() + ")"));
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
            out.println("Usage: seed list | seed use NAME | seed clear | seed edit default|NAME | seed gen NAME | seed rm NAME");
            return;
        }
        switch (args.getFirst()) {
            case "list" -> listSeeds();
            case "use" -> useSeed(requiredArg(args, "seed use requires a seed name"));
            case "clear" -> {
                session.environmentName(null);
                out.println(styler.success("Using seed: ") + styler.env("default"));
            }
            case "edit" -> {
                var name = requiredArg(args, "seed edit requires default or seed name");
                openEditor(seedPath(name));
            }
            case "gen" -> {
                var name = requiredArg(args, "seed gen requires a seed name");
                var created = requestFileCreator.createEnvironment(name, session.projectRoot());
                out.println(styler.success("Created seed: ") + styler.path(created.relativePath()));
            }
            case "rm" -> removeSeed(requiredArg(args, "seed rm requires a seed name"));
            default -> out.println("Unknown seed command: " + args.getFirst());
        }
    }

    private void useSeed(String name) throws DoppioException {
        if (DoppioEnvironment.isDefaultName(name)) {
            session.environmentName(null);
            out.println(styler.success("Using seed: ") + styler.env("default"));
            return;
        }
        var path = seedPath(name);
        if (!Files.isRegularFile(path)) {
            throw new DoppioException(ErrorKind.SEED, "Seed not found: " + name + ". Use `seed gen " + name + "` first.");
        }
        session.environmentName(name);
        out.println(styler.success("Using seed: ") + styler.env(name));
    }

    private void handleConfig(List<String> args) throws DoppioException {
        if (args.isEmpty() || !"editor".equals(args.getFirst())) {
            out.println("Usage: config editor show | config editor use COMMAND | config editor clear");
            return;
        }
        handleEditorConfig(args.subList(1, args.size()));
    }

    private void handleEditorConfig(List<String> args) throws DoppioException {
        if (args.isEmpty()) {
            out.println("Usage: config editor show | config editor use COMMAND | config editor clear");
            return;
        }
        switch (args.getFirst()) {
            case "show" -> printEditor();
            case "use" -> {
                var command = joinedArgs(args, 1, "config editor use requires a command");
                configStore.write(configStore.read().withEditorCommand(command));
                out.println(styler.success("Editor saved: ") + styler.command(command));
            }
            case "clear" -> {
                configStore.write(configStore.read().withEditorCommand(null));
                out.println(styler.muted("Editor config cleared."));
            }
            default -> out.println("Unknown config editor command: " + args.getFirst());
        }
    }

    private void printEditor() {
        var resolved = editor.resolve(environment, configStore.read());
        if (resolved.isEmpty()) {
            out.println(styler.warning("Editor: ") + "(not configured)");
            out.println(styler.muted("Run `config editor use nano`, `config editor use \"code -w\"`, or set DOPPIO_EDITOR, VISUAL, or EDITOR."));
            return;
        }
        out.println("Editor: " + styler.command(resolved.get().command()) + " " + styler.muted("(" + resolved.get().source() + ")"));
    }

    private void openEditor(Path file) throws DoppioException {
        var normalized = file.toAbsolutePath().normalize();
        var config = configStore.read();
        var resolved = editor.resolve(environment, config);
        if (resolved.isPresent()) {
            out.println(styler.muted("Opening ") + styler.path(normalized) + styler.muted(" with ") + styler.command(resolved.get().command()));
        }
        editor.open(normalized, environment, config);
        out.println(styler.success("Editor closed: ") + styler.path(normalized));
    }

    private void listSeeds() throws DoppioException {
        out.println("Seeds");
        var defaultSeed = session.doppioDirectory().resolve("default.seed");
        out.println("  " + styler.env("default") + " " + (Files.isRegularFile(defaultSeed) ? styler.path(defaultSeed) : styler.warning("(missing)")));
        var seeds = seedNames();
        for (var seed : seeds) {
            out.println("  " + styler.env(seed) + " " + styler.path(session.doppioDirectory().resolve("seeds").resolve(seed + ".seed")));
        }
    }

    private List<String> seedNames() throws DoppioException {
        var seedsDir = session.doppioDirectory().resolve("seeds");
        if (!Files.isDirectory(seedsDir)) {
            return List.of();
        }
        try (var files = Files.walk(seedsDir, 1)) {
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
        if (DoppioEnvironment.isDefaultName(name)) {
            return session.doppioDirectory().resolve("default.seed");
        }
        var seed = DoppioEnvironment.of(name);
        var path = session.doppioDirectory().resolve("seeds").resolve(seed.fileName()).normalize();
        if (!path.startsWith(session.doppioDirectory().resolve("seeds").normalize())) {
            throw new DoppioException(ErrorKind.SEED, "Seed path must stay inside .doppio/seeds");
        }
        return path;
    }

    private void removeSeed(String name) throws DoppioException {
        if (DoppioEnvironment.isDefaultName(name)) {
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
            out.println(styler.warning("Moved seed to trash: ") + styler.path(target));
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
            out.printf("  %s %s%n", styler.muted((i + 1) + "."), styler.path(recent.get(i).path()));
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
        out.println(styler.header("Doppio Run"));
        if (request.name() != null && !request.name().isBlank()) {
            out.println("Name: " + request.name());
        }
        out.println("Seed: " + styler.env(envName == null || envName.isBlank() ? "default" : envName));
        out.println("Request: " + styler.method(request.method().name()) + " " + styler.url(request.uri().toString()));
        out.println("Result: " + styler.result(label, report.isSuccess()) + " " + response.statusCode() + "  " + response.duration().toMillis() + "ms");
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
              seed list | seed use NAME | seed clear | seed edit default|NAME | seed gen NAME | seed rm NAME
              config editor show | config editor use COMMAND | config editor clear
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

    private String joinedArgs(List<String> args, int start, String message) throws DoppioException {
        if (args.size() <= start) {
            throw new DoppioException(ErrorKind.FILE, message);
        }
        var joined = String.join(" ", args.subList(start, args.size())).trim();
        if (joined.isBlank()) {
            throw new DoppioException(ErrorKind.FILE, message);
        }
        return joined;
    }

    private void ensureNoArgs(List<String> args, String usage) throws DoppioException {
        if (!args.isEmpty()) {
            throw new DoppioException(ErrorKind.PARSE, usage);
        }
    }

    private String effectiveEnv(String oneOffEnv) {
        return oneOffEnv == null ? session.environmentName() : oneOffEnv;
    }

    private String prompt() {
        return styler.prompt(session.promptEnvironment());
    }

    private void printProjectBanner() {
        out.println("Doppio project: " + styler.path(session.doppioDirectory()));
        out.println(styler.muted("Type `help` for commands."));
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
