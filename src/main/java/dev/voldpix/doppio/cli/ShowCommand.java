package dev.voldpix.doppio.cli;

import dev.voldpix.doppio.console.ConsoleFormatter;
import dev.voldpix.doppio.console.JsonFormatter;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.request.RequestFileInspector;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "show", mixinStandardHelpOptions = true, description = "Inspect a request file without executing it.")
public class ShowCommand implements Callable<Integer> {
    @Parameters(index = "0", paramLabel = "FILE", description = "The .dopo request file to inspect.")
    private Path file;

    @Option(names = "--json", description = "Print machine-readable JSON output.")
    private boolean json;

    private final Path workingDirectory;
    private final RequestFileInspector inspector;
    private final ConsoleFormatter formatter;
    private final JsonFormatter jsonFormatter;
    private final PrintWriter out;
    private final PrintWriter err;

    public ShowCommand(
        Path workingDirectory,
        RequestFileInspector inspector,
        ConsoleFormatter formatter,
        JsonFormatter jsonFormatter,
        PrintWriter out,
        PrintWriter err
    ) {
        this.workingDirectory = workingDirectory;
        this.inspector = inspector;
        this.formatter = formatter;
        this.jsonFormatter = jsonFormatter;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() {
        try {
            var inspection = inspector.inspect(file, workingDirectory);
            if (json) {
                out.println(jsonFormatter.formatShow(inspection));
                out.flush();
            } else {
                print(inspection);
            }
            return 0;
        } catch (DoppioException e) {
            if (json) {
                err.println(jsonFormatter.formatError(e));
                err.flush();
            } else {
                formatter.printError(e, err);
            }
            return 1;
        }
    }

    private void print(dev.voldpix.doppio.request.RequestFileInspection fileInspection) {
        var inspection = fileInspection.inspection();
        var request = inspection.request();
        var metadata = inspection.metadata();
        var displayName = request.name() == null || request.name().isBlank()
            ? fileStem(fileInspection.relativePath())
            : request.name();

        out.println("Request");
        out.println("  Name: " + displayName);
        out.println("  File: " + fileInspection.relativePath());
        out.println("  Path: " + fileInspection.requestFile());
        out.println("  Method: " + request.method());
        out.println("  URL: " + request.url());
        out.println("  Body: " + (request.body() == null ? "none" : request.body().kind()));

        if (!request.headers().isEmpty()) {
            out.println("  Headers:");
            request.headers().forEach(header -> out.println("    " + header.key() + "=" + header.value()));
        }

        if (!request.queryParams().isEmpty()) {
            out.println("  Query:");
            request.queryParams().forEach(query -> out.println("    " + query.name() + "=" + query.value()));
        }

        if (!metadata.variables().isEmpty()) {
            out.println("  Local Variables:");
            metadata.variables().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> out.println("    " + entry.getKey() + "=" + entry.getValue()));
        }

        out.flush();
    }

    private String fileStem(Path path) {
        var filename = path.getFileName().toString();
        var dot = filename.lastIndexOf('.');
        return dot == -1 ? filename : filename.substring(0, dot);
    }
}
