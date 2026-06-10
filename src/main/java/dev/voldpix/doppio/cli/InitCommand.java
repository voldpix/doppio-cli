package dev.voldpix.doppio.cli;

import picocli.CommandLine.Command;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "init", mixinStandardHelpOptions = true, description = "Create a Doppio project scaffold.")
public class InitCommand implements Callable<Integer> {
    private static final String DEFAULT_SEED = """
        # Default values used by {{VARIABLE}} placeholders in .dopo files.
        BASE_URL=https://httpbin.org
        USERNAME=voldpix
        TEST_ID=42
        """;

    private static final String EXAMPLE_DOPO = """
        @name Create sample user
        @var ROLE=admin
        POST {{BASE_URL}}/post
        -h Content-Type=application/json
        -q source=doppio

        <json|
        {
          "username": "{{USERNAME}}",
          "role": "{{ROLE}}"
        }
        |>
        """;

    private static final String TEST_DOPO = """
        @name Smoke test
        GET {{BASE_URL}}/get
        -h Accept=application/json
        -q testId={{TEST_ID}}
        -q source=doppio
        """;

    private final Path workingDirectory;
    private final PrintWriter out;
    private final PrintWriter err;

    public InitCommand(Path workingDirectory, PrintWriter out, PrintWriter err) {
        this.workingDirectory = workingDirectory;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() {
        try {
            var doppioDir = workingDirectory.resolve(".doppio").toAbsolutePath().normalize();
            var seedsDir = doppioDir.resolve("seeds");
            var recipesDir = doppioDir.resolve("recipes");
            Files.createDirectories(doppioDir);
            Files.createDirectories(seedsDir);
            Files.createDirectories(recipesDir);

            writeIfMissing(doppioDir.resolve("default.seed"), DEFAULT_SEED);
            writeIfMissing(recipesDir.resolve("example.dopo"), EXAMPLE_DOPO);
            writeIfMissing(recipesDir.resolve("test.dopo"), TEST_DOPO);

            out.println("Initialized Doppio project");
            out.println();
            out.println(doppioDir);
            out.println("|-- default.seed");
            out.println("|-- seeds/");
            out.println("`-- recipes/");
            out.println("    |-- example.dopo");
            out.println("    `-- test.dopo");
            out.flush();
            return 0;
        } catch (IOException e) {
            err.println("Init Error: " + e.getMessage());
            err.flush();
            return 1;
        }
    }

    private void writeIfMissing(Path path, String content) throws IOException {
        if (!Files.exists(path)) {
            Files.writeString(path, content);
        }
    }
}
