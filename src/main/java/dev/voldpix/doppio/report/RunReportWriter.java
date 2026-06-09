package dev.voldpix.doppio.report;

import dev.voldpix.doppio.console.ConsoleFormatter;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;
import dev.voldpix.doppio.model.RunReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

public class RunReportWriter {
    private final ConsoleFormatter formatter;
    private final Clock clock;

    public RunReportWriter() {
        this(new ConsoleFormatter(false), Clock.systemUTC());
    }

    public RunReportWriter(ConsoleFormatter formatter, Clock clock) {
        this.formatter = formatter;
        this.clock = clock;
    }

    public Path write(RunReport report) throws DoppioException {
        var requestFile = report.requestFile();
        var parent = requestFile.getParent();
        var outputFile = parent.resolve(fileStem(requestFile) + "-" + clock.millis() + ".txt");

        try {
            Files.writeString(outputFile, formatter.formatReport(report));
            return outputFile;
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.FILE, "Unable to save report: " + outputFile, e);
        }
    }

    private String fileStem(Path path) {
        var filename = path.getFileName().toString();
        var dot = filename.lastIndexOf('.');
        return dot == -1 ? filename : filename.substring(0, dot);
    }
}
