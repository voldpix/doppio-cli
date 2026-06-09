package dev.voldpix.doppio.cli;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.report.ReportCleaner;
import picocli.CommandLine.Command;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "clean", mixinStandardHelpOptions = true, description = "Remove saved Doppio run reports.")
public class CleanCommand implements Callable<Integer> {
    private final Path workingDirectory;
    private final ReportCleaner cleaner;
    private final PrintWriter out;
    private final PrintWriter err;

    public CleanCommand(Path workingDirectory, ReportCleaner cleaner, PrintWriter out, PrintWriter err) {
        this.workingDirectory = workingDirectory;
        this.cleaner = cleaner;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() {
        try {
            var removed = cleaner.clean(workingDirectory);
            out.printf("Removed %d saved report%s%n", removed, removed == 1 ? "" : "s");
            out.flush();
            return 0;
        } catch (DoppioException e) {
            err.println("Clean Error: " + e.getMessage());
            err.flush();
            return 1;
        }
    }
}
