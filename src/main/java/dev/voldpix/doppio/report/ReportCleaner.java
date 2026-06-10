package dev.voldpix.doppio.report;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;
import dev.voldpix.doppio.pipeline.DoppioProjectResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class ReportCleaner {
    private static final Pattern REPORT_FILE = Pattern.compile(".+-\\d{13}\\.txt");

    private final DoppioProjectResolver projectResolver;

    public ReportCleaner() {
        this(new DoppioProjectResolver());
    }

    public ReportCleaner(DoppioProjectResolver projectResolver) {
        this.projectResolver = projectResolver;
    }

    public int clean(Path workingDirectory) throws DoppioException {
        var doppioDir = projectResolver.findDoppioDirectory(workingDirectory);
        if (doppioDir == null) {
            throw new DoppioException(ErrorKind.FILE, "No .doppio project found");
        }

        var requestsDir = doppioDir.resolve("recipes");
        if (!Files.isDirectory(requestsDir)) {
            return 0;
        }

        try (var files = Files.walk(requestsDir)) {
            var reportFiles = files
                .filter(Files::isRegularFile)
                .filter(path -> REPORT_FILE.matcher(path.getFileName().toString()).matches())
                .toList();

            for (var file : reportFiles) {
                Files.delete(file);
            }

            return reportFiles.size();
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.FILE, "Unable to clean reports under: " + requestsDir, e);
        }
    }
}
