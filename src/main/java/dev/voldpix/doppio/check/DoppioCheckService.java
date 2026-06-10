package dev.voldpix.doppio.check;

import dev.voldpix.doppio.dsl.DslParseException;
import dev.voldpix.doppio.env.DoppioEnvironment;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;
import dev.voldpix.doppio.pipeline.DoppioPipeline;
import dev.voldpix.doppio.pipeline.DoppioProjectResolver;
import dev.voldpix.doppio.pipeline.RequestFileResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class DoppioCheckService {
    private final RequestFileResolver requestFileResolver;
    private final DoppioProjectResolver projectResolver;
    private final DoppioPipeline pipeline;

    public DoppioCheckService(DoppioPipeline pipeline) {
        this(new RequestFileResolver(), new DoppioProjectResolver(), pipeline);
    }

    public DoppioCheckService(
        RequestFileResolver requestFileResolver,
        DoppioProjectResolver projectResolver,
        DoppioPipeline pipeline
    ) {
        this.requestFileResolver = requestFileResolver;
        this.projectResolver = projectResolver;
        this.pipeline = pipeline;
    }

    public CheckSummary check(Path requestedPath, Path workingDirectory, Map<String, String> environment)
        throws DoppioException {
        return check(requestedPath, workingDirectory, environment, DoppioEnvironment.none());
    }

    public CheckSummary check(
        Path requestedPath,
        Path workingDirectory,
        Map<String, String> environment,
        DoppioEnvironment selectedEnvironment
    ) throws DoppioException {
        selectedEnvironment = selectedEnvironment == null ? DoppioEnvironment.none() : selectedEnvironment;
        var cwd = workingDirectory.toAbsolutePath().normalize();
        var doppioDir = projectResolver.findDoppioDirectory(cwd);
        if (selectedEnvironment.selected() && doppioDir == null) {
            throw new DoppioException(ErrorKind.SEED, "--env can only be used inside a Doppio project");
        }
        var files = resolveFiles(requestedPath, cwd, doppioDir);
        var effectiveEnvironment = selectedEnvironment;
        return new CheckSummary(files.stream()
            .map(file -> checkFile(file, cwd, doppioDir, environment, effectiveEnvironment))
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

    private CheckResult checkFile(
        Path file,
        Path cwd,
        Path doppioDir,
        Map<String, String> environment,
        DoppioEnvironment selectedEnvironment
    ) {
        var displayPath = displayPath(file, cwd, doppioDir);
        try {
            pipeline.preview(file, cwd, environment, selectedEnvironment);
            return new CheckResult(file, displayPath, CheckStatus.VALID, null);
        } catch (DoppioException e) {
            return new CheckResult(file, displayPath, CheckStatus.FAILED, message(e));
        }
    }

    private String message(DoppioException exception) {
        if (exception instanceof DslParseException parseException && !parseException.errors().isEmpty()) {
            return exception.getMessage() + ": " + parseException.errors().getFirst().hint();
        }
        return exception.getMessage();
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
