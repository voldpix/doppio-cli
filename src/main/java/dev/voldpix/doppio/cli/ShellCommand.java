package dev.voldpix.doppio.cli;

import dev.voldpix.doppio.http.HttpTransport;
import dev.voldpix.doppio.pipeline.DoppioPipeline;
import dev.voldpix.doppio.shell.DoppioShell;
import dev.voldpix.doppio.shell.DoppioStatusStore;
import dev.voldpix.doppio.shell.ShellCompleter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "shell", mixinStandardHelpOptions = true, description = "Open an interactive Doppio shell.")
public class ShellCommand implements Callable<Integer> {
    @Option(names = "--project", paramLabel = "PATH", description = "Project path to open.")
    private Path project;

    @Option(names = "--env", paramLabel = "NAME", description = "Initial shell env.")
    private String envName;

    private final Path workingDirectory;
    private final Map<String, String> environment;
    private final DoppioPipeline pipeline;
    private final HttpTransport transport;
    private final PrintWriter out;
    private final PrintWriter err;

    public ShellCommand(
        Path workingDirectory,
        Map<String, String> environment,
        DoppioPipeline pipeline,
        HttpTransport transport,
        PrintWriter out,
        PrintWriter err
    ) {
        this.workingDirectory = workingDirectory;
        this.environment = Map.copyOf(environment);
        this.pipeline = pipeline;
        this.transport = transport;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() {
        var statusStore = DoppioStatusStore.userDefault();
        try {
            Files.createDirectories(statusStore.configDirectory());
            var shell = new DoppioShell(workingDirectory, environment, pipeline, transport, statusStore, out, err);
            var terminalBuilder = TerminalBuilder.builder()
                .ffm(false)
                .jni(false)
                .jna(false)
                .jansi(false)
                .color(!environment.containsKey("NO_COLOR"));
            if (System.console() == null) {
                terminalBuilder
                    .system(false)
                    .streams(System.in, System.out)
                    .dumb(true);
            } else {
                terminalBuilder.system(true);
            }
            var terminal = terminalBuilder.build();
            var reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(new DefaultParser())
                .completer(new ShellCompleter(shell))
                .build();
            reader.setVariable(LineReader.HISTORY_FILE, statusStore.historyFile());
            return shell.run(reader, project, envName);
        } catch (IOException e) {
            err.println("Shell Error: Unable to start terminal: " + e.getMessage());
            err.flush();
            return 1;
        }
    }
}
