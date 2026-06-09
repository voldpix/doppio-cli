package dev.voldpix.doppio.cli;

import dev.voldpix.doppio.console.ConsoleFormatter;
import dev.voldpix.doppio.http.HttpTransport;
import dev.voldpix.doppio.pipeline.DoppioPipeline;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
    name = "doppio",
    mixinStandardHelpOptions = true,
    version = "doppio 1.0-SNAPSHOT",
    description = "Console-first HTTP request execution."
)
public class DoppioCommand implements Callable<Integer> {
    @Spec
    private CommandSpec spec;

    public static CommandLine commandLine() {
        return commandLine(
            Path.of("").toAbsolutePath(),
            System.getenv(),
            new PrintWriter(System.out, true),
            new PrintWriter(System.err, true),
            null
        );
    }

    public static CommandLine commandLine(
        Path workingDirectory,
        Map<String, String> environment,
        PrintWriter out,
        PrintWriter err,
        HttpTransport transport
    ) {
        var formatter = new ConsoleFormatter();
        var pipeline = transport == null
            ? new DoppioPipeline()
            : new DoppioPipeline(
                new dev.voldpix.doppio.pipeline.RequestFileResolver(),
                new dev.voldpix.doppio.seed.SeedFileLoader(),
                new dev.voldpix.doppio.template.TemplateEngine(),
                new dev.voldpix.doppio.dsl.DslProcessor(),
                new dev.voldpix.doppio.json.JsonBodyProcessor(),
                new dev.voldpix.doppio.http.RequestPreparer(),
                transport,
                DoppioPipeline.DEFAULT_TIMEOUT
            );

        var commandLine = new CommandLine(new DoppioCommand());
        commandLine.addSubcommand("init", new InitCommand(workingDirectory, out));
        commandLine.addSubcommand("run", new RunCommand(workingDirectory, environment, pipeline, formatter, out, err));
        commandLine.setOut(out);
        commandLine.setErr(err);
        return commandLine;
    }

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return 0;
    }
}
