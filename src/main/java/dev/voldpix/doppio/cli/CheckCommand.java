package dev.voldpix.doppio.cli;

import dev.voldpix.doppio.check.CheckResult;
import dev.voldpix.doppio.check.DoppioCheckService;
import dev.voldpix.doppio.env.DoppioEnvironment;
import dev.voldpix.doppio.model.DoppioException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "check", mixinStandardHelpOptions = true, description = "Validate .dopo request files without executing HTTP.")
public class CheckCommand implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", paramLabel = "FILE_OR_FOLDER", description = "Optional request file or folder shorthand.")
    private Path target;

    @Option(names = "--env", paramLabel = "NAME", description = "Use .doppio/envs/NAME.seed over default.seed.")
    private String envName;

    private final Path workingDirectory;
    private final Map<String, String> environment;
    private final DoppioCheckService checkService;
    private final PrintWriter out;
    private final PrintWriter err;

    public CheckCommand(
        Path workingDirectory,
        Map<String, String> environment,
        DoppioCheckService checkService,
        PrintWriter out,
        PrintWriter err
    ) {
        this.workingDirectory = workingDirectory;
        this.environment = Map.copyOf(environment);
        this.checkService = checkService;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() {
        try {
            var selectedEnvironment = DoppioEnvironment.of(envName);
            var summary = checkService.check(target, workingDirectory, environment, selectedEnvironment);
            out.println("Doppio Check");
            if (selectedEnvironment.selected()) {
                out.println("Env: " + selectedEnvironment.name());
            }
            out.println();
            out.println("Valid: " + summary.validCount());
            out.println("Failed: " + summary.failedCount());

            if (!summary.results().isEmpty()) {
                out.println();
                summary.results().forEach(this::printResult);
            }

            out.flush();
            return summary.hasFailures() ? 1 : 0;
        } catch (DoppioException e) {
            err.println("Check Error: " + e.getMessage());
            err.flush();
            return 1;
        }
    }

    private void printResult(CheckResult result) {
        out.printf("%-7s %s", result.status().name().toLowerCase(), result.displayPath());
        if (result.failed()) {
            out.print(" - " + result.message());
        }
        out.println();
    }
}
