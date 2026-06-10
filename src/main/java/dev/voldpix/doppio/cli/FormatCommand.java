package dev.voldpix.doppio.cli;

import dev.voldpix.doppio.format.DopoFormatService;
import dev.voldpix.doppio.format.FormatResult;
import dev.voldpix.doppio.model.DoppioException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "format", mixinStandardHelpOptions = true, description = "Format .dopo request files.")
public class FormatCommand implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", paramLabel = "FILE_OR_FOLDER", description = "Optional request file or folder shorthand.")
    private Path target;

    private final Path workingDirectory;
    private final DopoFormatService formatService;
    private final PrintWriter out;
    private final PrintWriter err;

    public FormatCommand(Path workingDirectory, DopoFormatService formatService, PrintWriter out, PrintWriter err) {
        this.workingDirectory = workingDirectory;
        this.formatService = formatService;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() {
        try {
            var summary = formatService.format(target, workingDirectory);
            out.println("Doppio Format");
            out.println();
            out.println("Changed: " + summary.changedCount());
            out.println("Unchanged: " + summary.unchangedCount());
            out.println("Failed: " + summary.failedCount());

            if (!summary.results().isEmpty()) {
                out.println();
                summary.results().forEach(this::printResult);
            }

            out.flush();
            return summary.hasFailures() ? 1 : 0;
        } catch (DoppioException e) {
            err.println("Format Error: " + e.getMessage());
            err.flush();
            return 1;
        }
    }

    private void printResult(FormatResult result) {
        out.printf("%-9s %s", result.status().name().toLowerCase(), result.displayPath());
        if (result.failed()) {
            out.print(" - " + result.message());
        }
        out.println();
    }
}
