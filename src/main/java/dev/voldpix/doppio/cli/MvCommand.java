package dev.voldpix.doppio.cli;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.request.RequestFileOperator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "mv", aliases = "move", mixinStandardHelpOptions = true, description = "Move a recipe file under .doppio/recipes.")
public class MvCommand implements Callable<Integer> {
    @Parameters(index = "0", paramLabel = "SOURCE", description = "Recipe path to move.")
    private Path source;

    @Parameters(index = "1", paramLabel = "DESTINATION", description = "Destination recipe path.")
    private Path destination;

    private final Path workingDirectory;
    private final RequestFileOperator operator;
    private final PrintWriter out;
    private final PrintWriter err;

    public MvCommand(Path workingDirectory, RequestFileOperator operator, PrintWriter out, PrintWriter err) {
        this.workingDirectory = workingDirectory;
        this.operator = operator;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() {
        try {
            var moved = operator.move(source, destination, workingDirectory);
            out.println("Recipe moved");
            out.println("  From: " + moved.sourceRelativePath());
            out.println("  To: " + moved.destinationRelativePath());
            out.flush();
            return 0;
        } catch (DoppioException e) {
            err.println("Move Error: " + e.getMessage());
            err.flush();
            return 1;
        }
    }
}
