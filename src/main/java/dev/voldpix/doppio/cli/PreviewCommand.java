package dev.voldpix.doppio.cli;

import dev.voldpix.doppio.console.ConsoleFormatter;
import dev.voldpix.doppio.console.JsonFormatter;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.pipeline.DoppioPipeline;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "preview", mixinStandardHelpOptions = true, description = "Hydrate and prepare a request without executing HTTP.")
public class PreviewCommand implements Callable<Integer> {
    @Parameters(index = "0", paramLabel = "FILE", description = "The .dopo request file to preview.")
    private Path file;

    @Option(names = "--json", description = "Print machine-readable JSON output.")
    private boolean json;

    private final Path workingDirectory;
    private final Map<String, String> environment;
    private final DoppioPipeline pipeline;
    private final ConsoleFormatter formatter;
    private final JsonFormatter jsonFormatter;
    private final PrintWriter out;
    private final PrintWriter err;

    public PreviewCommand(
        Path workingDirectory,
        Map<String, String> environment,
        DoppioPipeline pipeline,
        ConsoleFormatter formatter,
        JsonFormatter jsonFormatter,
        PrintWriter out,
        PrintWriter err
    ) {
        this.workingDirectory = workingDirectory;
        this.environment = Map.copyOf(environment);
        this.pipeline = pipeline;
        this.formatter = formatter;
        this.jsonFormatter = jsonFormatter;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() {
        try {
            var report = pipeline.preview(file, workingDirectory, environment);
            if (json) {
                out.println(jsonFormatter.formatPreview(report));
                out.flush();
            } else {
                formatter.printPreview(report, out);
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
}
