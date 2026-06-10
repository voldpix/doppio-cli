package dev.voldpix.doppio.format;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;
import dev.voldpix.doppio.pipeline.DoppioProjectResolver;
import dev.voldpix.doppio.pipeline.RequestFileResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class DopoFormatService {
    private final RequestFileResolver requestFileResolver;
    private final DoppioProjectResolver projectResolver;
    private final DopoSourceFormatter sourceFormatter;

    public DopoFormatService() {
        this(new RequestFileResolver(), new DoppioProjectResolver(), new DopoSourceFormatter());
    }

    public DopoFormatService(
        RequestFileResolver requestFileResolver,
        DoppioProjectResolver projectResolver,
        DopoSourceFormatter sourceFormatter
    ) {
        this.requestFileResolver = requestFileResolver;
        this.projectResolver = projectResolver;
        this.sourceFormatter = sourceFormatter;
    }

    public FormatSummary format(Path requestedPath, Path workingDirectory) throws DoppioException {
        var cwd = workingDirectory.toAbsolutePath().normalize();
        var doppioDir = projectResolver.findDoppioDirectory(cwd);
        var files = resolveFiles(requestedPath, cwd, doppioDir);
        return new FormatSummary(files.stream()
            .map(file -> formatFile(file, cwd, doppioDir))
            .toList());
    }

    private List<Path> resolveFiles(Path requestedPath, Path cwd, Path doppioDir) throws DoppioException {
        if (requestedPath == null) {
            if (doppioDir == null) {
                throw new DoppioException(ErrorKind.FILE, "No .doppio project found. Run `doppio init` first.");
            }
            return dopoFiles(doppioDir.resolve("requests"));
        }

        var directCandidate = requestedPath.isAbsolute()
            ? requestedPath.normalize()
            : cwd.resolve(requestedPath).normalize();
        if (doppioDir != null && Files.isDirectory(directCandidate)) {
            return dopoFiles(directCandidate);
        }

        if (doppioDir != null && !requestedPath.isAbsolute()) {
            var requestsDir = doppioDir.resolve("requests").toAbsolutePath().normalize();
            var requestsCandidate = requestsDir.resolve(requestedPath).normalize();
            if (requestsCandidate.startsWith(requestsDir) && Files.isDirectory(requestsCandidate)) {
                return dopoFiles(requestsCandidate);
            }
        }

        return List.of(requestFileResolver.resolve(requestedPath, cwd).requestFile().toAbsolutePath().normalize());
    }

    private List<Path> dopoFiles(Path directory) throws DoppioException {
        if (!Files.isDirectory(directory)) {
            throw new DoppioException(ErrorKind.FILE, "Request folder not found: " + directory);
        }

        try (var files = Files.walk(directory)) {
            return files
                .filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().endsWith(".dopo"))
                .map(file -> file.toAbsolutePath().normalize())
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.FILE, "Unable to list request files: " + directory, e);
        }
    }

    private FormatResult formatFile(Path file, Path cwd, Path doppioDir) {
        var displayPath = displayPath(file, cwd, doppioDir);
        try {
            var original = Files.readString(file);
            var formatted = sourceFormatter.format(original);
            if (original.equals(formatted)) {
                return new FormatResult(file, displayPath, FormatStatus.UNCHANGED, null);
            }
            Files.writeString(file, formatted);
            return new FormatResult(file, displayPath, FormatStatus.CHANGED, null);
        } catch (DoppioException e) {
            return new FormatResult(file, displayPath, FormatStatus.FAILED, e.getMessage());
        } catch (IOException e) {
            return new FormatResult(file, displayPath, FormatStatus.FAILED, "Unable to read or write file: " + e.getMessage());
        }
    }

    private String displayPath(Path file, Path cwd, Path doppioDir) {
        if (doppioDir != null) {
            var requestsDir = doppioDir.resolve("requests").toAbsolutePath().normalize();
            if (file.startsWith(requestsDir)) {
                return requestsDir.relativize(file).toString();
            }
        }
        if (file.startsWith(cwd)) {
            return cwd.relativize(file).toString();
        }
        return file.toString();
    }
}
