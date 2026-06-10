package dev.voldpix.doppio.cli;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.request.RequestFileOperator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "rename", mixinStandardHelpOptions = true, description = "Rename a recipe file in its current folder.")
public class RenameCommand implements Callable<Integer> {
    @Parameters(index = "0", paramLabel = "SOURCE", description = "Recipe path to rename.")
    private Path source;

    @Parameters(index = "1", paramLabel = "NAME", description = "New recipe file name.")
    private Path name;

    private final Path workingDirectory;
    private final RequestFileOperator operator;
    private final PrintWriter out;
    private final PrintWriter err;

    public RenameCommand(Path workingDirectory, RequestFileOperator operator, PrintWriter out, PrintWriter err) {
        this.workingDirectory = workingDirectory;
        this.operator = operator;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() {
        try {
            var renamed = operator.rename(source, name, workingDirectory);
            out.println("Recipe renamed");
            out.println("  From: " + renamed.sourceRelativePath());
            out.println("  To: " + renamed.destinationRelativePath());
            out.flush();
            return 0;
        } catch (DoppioException e) {
            err.println("Rename Error: " + e.getMessage());
            err.flush();
            return 1;
        }
    }
}
