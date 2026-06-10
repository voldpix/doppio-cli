package dev.voldpix.doppio.cli;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.request.RequestFileRemover;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "rm", mixinStandardHelpOptions = true, description = "Move a recipe file to .doppio/trash.")
public class RmCommand implements Callable<Integer> {
    @Parameters(index = "0", paramLabel = "FILE", description = "Request path to remove from .doppio/recipes.")
    private Path file;

    private final Path workingDirectory;
    private final RequestFileRemover remover;
    private final PrintWriter out;
    private final PrintWriter err;

    public RmCommand(Path workingDirectory, RequestFileRemover remover, PrintWriter out, PrintWriter err) {
        this.workingDirectory = workingDirectory;
        this.remover = remover;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() {
        try {
            var removed = remover.remove(file, workingDirectory);
            out.println("Moved recipe to trash");
            out.println("  File: " + removed.relativePath());
            out.println("  Trash: " + removed.trashFile());
            out.flush();
            return 0;
        } catch (DoppioException e) {
            err.println("Remove Error: " + e.getMessage());
            err.flush();
            return 1;
        }
    }
}
