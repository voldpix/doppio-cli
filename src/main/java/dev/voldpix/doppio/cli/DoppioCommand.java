package dev.voldpix.doppio.cli;

import dev.voldpix.doppio.check.DoppioCheckService;
import dev.voldpix.doppio.console.ConsoleFormatter;
import dev.voldpix.doppio.console.JsonFormatter;
import dev.voldpix.doppio.doctor.DoppioDoctorService;
import dev.voldpix.doppio.format.DopoFormatService;
import dev.voldpix.doppio.http.HttpTransport;
import dev.voldpix.doppio.list.RequestListService;
import dev.voldpix.doppio.pipeline.DoppioPipeline;
import dev.voldpix.doppio.request.RequestFileCreator;
import dev.voldpix.doppio.request.RequestFileInspector;
import dev.voldpix.doppio.request.RequestFileOperator;
import dev.voldpix.doppio.request.RequestFileRemover;
import dev.voldpix.doppio.report.ReportCleaner;
import dev.voldpix.doppio.report.RunReportWriter;
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
    description = "Brew HTTP requests from the console."
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
        var jsonFormatter = new JsonFormatter();
        var pipeline = transport == null
            ? new DoppioPipeline()
            : new DoppioPipeline(
                new dev.voldpix.doppio.pipeline.RequestFileResolver(),
                new dev.voldpix.doppio.seed.SeedFileLoader(),
                new dev.voldpix.doppio.template.TemplateEngine(),
                new dev.voldpix.doppio.dsl.DslProcessor(),
                new dev.voldpix.doppio.body.BodyProcessor(),
                new dev.voldpix.doppio.http.RequestPreparer(),
                transport,
                DoppioPipeline.DEFAULT_TIMEOUT
            );

        var commandLine = new CommandLine(new DoppioCommand());
        commandLine.addSubcommand("init", new InitCommand(workingDirectory, out, err));
        commandLine.addSubcommand("gen", new GenCommand(workingDirectory, new RequestFileCreator(), out, err));
        commandLine.addSubcommand("run", new RunCommand(workingDirectory, environment, pipeline, formatter, jsonFormatter, new RunReportWriter(), out, err));
        commandLine.addSubcommand("show", new ShowCommand(workingDirectory, new RequestFileInspector(), formatter, jsonFormatter, out, err));
        commandLine.addSubcommand("preview", new PreviewCommand(workingDirectory, environment, pipeline, formatter, jsonFormatter, out, err));
        commandLine.addSubcommand("list", new ListCommand(workingDirectory, new RequestListService(), jsonFormatter, out, err));
        commandLine.addSubcommand("format", new FormatCommand(workingDirectory, new DopoFormatService(), out, err));
        commandLine.addSubcommand("check", new CheckCommand(workingDirectory, environment, new DoppioCheckService(pipeline), out, err));
        commandLine.addSubcommand("doctor", new DoctorCommand(workingDirectory, environment, new DoppioDoctorService(new DoppioCheckService(pipeline)), out));
        commandLine.addSubcommand("shell", new ShellCommand(workingDirectory, environment, pipeline, transport, out, err));
        commandLine.addSubcommand("clean", new CleanCommand(workingDirectory, new ReportCleaner(), out, err));
        commandLine.addSubcommand("rm", new RmCommand(workingDirectory, new RequestFileRemover(), out, err));
        commandLine.addSubcommand("mv", new MvCommand(workingDirectory, new RequestFileOperator(), out, err));
        commandLine.addSubcommand("cp", new CpCommand(workingDirectory, new RequestFileOperator(), out, err));
        commandLine.addSubcommand("rename", new RenameCommand(workingDirectory, new RequestFileOperator(), out, err));
        commandLine.addSubcommand("docs", new DocsCommand(out));
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
