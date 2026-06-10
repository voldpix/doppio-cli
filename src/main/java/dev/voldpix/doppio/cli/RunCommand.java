package dev.voldpix.doppio.cli;

import dev.voldpix.doppio.console.ConsoleFormatter;
import dev.voldpix.doppio.console.JsonFormatter;
import dev.voldpix.doppio.env.DoppioEnvironment;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.pipeline.DoppioPipeline;
import dev.voldpix.doppio.report.RunReportWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "run", mixinStandardHelpOptions = true, description = "Execute a .dopo request file.")
public class RunCommand implements Callable<Integer> {
    @Parameters(index = "0", paramLabel = "FILE", description = "The .dopo request file to execute.")
    private Path file;

    @Option(names = "--save", description = "Save the rendered run report next to the resolved request file.")
    private boolean save;

    @Option(names = "--json", description = "Print machine-readable JSON output.")
    private boolean json;

    @Option(names = "--env", paramLabel = "NAME", description = "Use .doppio/seeds/NAME.seed over default.seed.")
    private String envName;

    private final Path workingDirectory;
    private final Map<String, String> environment;
    private final DoppioPipeline pipeline;
    private final ConsoleFormatter formatter;
    private final JsonFormatter jsonFormatter;
    private final RunReportWriter reportWriter;
    private final PrintWriter out;
    private final PrintWriter err;

    public RunCommand(
        Path workingDirectory,
        Map<String, String> environment,
        DoppioPipeline pipeline,
        ConsoleFormatter formatter,
        JsonFormatter jsonFormatter,
        RunReportWriter reportWriter,
        PrintWriter out,
        PrintWriter err
    ) {
        this.workingDirectory = workingDirectory;
        this.environment = Map.copyOf(environment);
        this.pipeline = pipeline;
        this.formatter = formatter;
        this.jsonFormatter = jsonFormatter;
        this.reportWriter = reportWriter;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() {
        try {
            var report = pipeline.run(file, workingDirectory, environment, DoppioEnvironment.of(envName));
            Path savedReport = null;
            if (save) {
                savedReport = reportWriter.write(report);
            }
            if (json) {
                out.println(jsonFormatter.formatRun(report, savedReport));
                out.flush();
            } else {
                formatter.printReport(report, out);
                if (savedReport != null) {
                    formatter.printSavedReport(savedReport, out);
                }
            }
            return report.isSuccess() ? 0 : 1;
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
