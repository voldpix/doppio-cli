package dev.voldpix.doppio.cli;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.request.RequestFileCreator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "gen", mixinStandardHelpOptions = true, description = "Create a request file under .doppio/requests.")
public class GenCommand implements Callable<Integer> {
    @Parameters(index = "0", paramLabel = "FILE", description = "Request path to create, with or without .dopo.")
    private Path file;

    private final Path workingDirectory;
    private final RequestFileCreator creator;
    private final PrintWriter out;
    private final PrintWriter err;

    public GenCommand(Path workingDirectory, RequestFileCreator creator, PrintWriter out, PrintWriter err) {
        this.workingDirectory = workingDirectory;
        this.creator = creator;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() {
        try {
            var created = creator.create(file, workingDirectory);
            out.println("Created request");
            out.println("  File: " + created.relativePath());
            out.println("  Path: " + created.requestFile());
            out.flush();
            return 0;
        } catch (DoppioException e) {
            err.println("Generate Error: " + e.getMessage());
            err.flush();
            return 1;
        }
    }
}
