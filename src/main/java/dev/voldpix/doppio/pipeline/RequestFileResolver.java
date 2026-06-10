package dev.voldpix.doppio.pipeline;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;

import java.nio.file.Files;
import java.nio.file.Path;

public class RequestFileResolver {
    private final DoppioProjectResolver projectResolver;

    public RequestFileResolver() {
        this(new DoppioProjectResolver());
    }

    public RequestFileResolver(DoppioProjectResolver projectResolver) {
        this.projectResolver = projectResolver;
    }

    public RequestResolution resolve(Path requestedFile, Path workingDirectory) throws DoppioException {
        var normalizedWorkingDirectory = workingDirectory.toAbsolutePath().normalize();
        var doppioDir = projectResolver.findDoppioDirectory(normalizedWorkingDirectory);
        var effectiveRequestFile = effectiveRequestFile(requestedFile, doppioDir);
        var directCandidate = normalize(normalizedWorkingDirectory, effectiveRequestFile);

        if (requestedFile.isAbsolute()) {
            return existingDirectFile(directCandidate, doppioDir);
        }

        if (doppioDir == null) {
            return existingStandaloneFile(directCandidate, requestedFile);
        }

        var seedFile = seedFile(doppioDir);
        var requestsDir = doppioDir.resolve("requests").toAbsolutePath().normalize();
        if (isInside(normalizedWorkingDirectory, requestsDir) && Files.exists(directCandidate)) {
            return existingProjectRequestFile(directCandidate, seedFile, requestsDir, requestedFile);
        }

        if (isExplicitDoppioPath(requestedFile) && Files.exists(directCandidate)) {
            return existingProjectRequestFile(directCandidate, seedFile, requestsDir, requestedFile);
        }

        var requestsCandidate = requestsDir.resolve(effectiveRequestFile).normalize();
        if (!requestsCandidate.startsWith(requestsDir)) {
            throw new DoppioException(
                ErrorKind.FILE,
                "Request path must stay inside .doppio/requests: " + requestedFile
            );
        }
        if (Files.exists(requestsCandidate)) {
            return existingFile(requestsCandidate, seedFile);
        }

        throw new DoppioException(
            ErrorKind.FILE,
            "Request file not found in .doppio/requests: " + requestedFile
        );
    }

    private Path effectiveRequestFile(Path requestedFile, Path doppioDir) throws DoppioException {
        var filename = requestedFile.getFileName();
        if (filename == null) {
            throw new DoppioException(ErrorKind.FILE, "Request file is required");
        }

        var filenameText = filename.toString();
        if (filenameText.endsWith(".dopo")) {
            return requestedFile;
        }

        if (filenameText.contains(".") || requestedFile.isAbsolute() || doppioDir == null) {
            throw new DoppioException(ErrorKind.FILE, "Only .dopo request files are supported: " + requestedFile);
        }

        return requestedFile.resolveSibling(filenameText + ".dopo");
    }

    private RequestResolution existingDirectFile(Path directCandidate, Path doppioDir) throws DoppioException {
        var seedFile = doppioDir == null ? null : seedFile(doppioDir);
        return existingFile(directCandidate, seedFile);
    }

    private Path seedFile(Path doppioDir) {
        var defaultSeed = doppioDir.resolve("default.seed");
        if (Files.exists(defaultSeed)) {
            return defaultSeed;
        }
        return doppioDir.resolve("local.seed");
    }

    private RequestResolution existingStandaloneFile(Path directCandidate, Path requestedFile) throws DoppioException {
        if (Files.exists(directCandidate)) {
            return existingFile(directCandidate, null);
        }

        throw new DoppioException(
            ErrorKind.FILE,
            "No .doppio project found and request file not found: " + requestedFile
        );
    }

    private RequestResolution existingFile(Path file, Path seedFile) throws DoppioException {
        if (!Files.exists(file)) {
            throw new DoppioException(ErrorKind.FILE, "Request file not found: " + file);
        }
        if (!Files.isRegularFile(file)) {
            throw new DoppioException(ErrorKind.FILE, "Request path is not a file: " + file);
        }
        return new RequestResolution(file, seedFile);
    }

    private RequestResolution existingProjectRequestFile(
        Path file,
        Path seedFile,
        Path requestsDir,
        Path requestedFile
    ) throws DoppioException {
        if (!file.toAbsolutePath().normalize().startsWith(requestsDir)) {
            throw new DoppioException(
                ErrorKind.FILE,
                "Request path must stay inside .doppio/requests: " + requestedFile
            );
        }
        return existingFile(file, seedFile);
    }

    private Path normalize(Path workingDirectory, Path requestedFile) {
        if (requestedFile.isAbsolute()) {
            return requestedFile.normalize();
        }
        return workingDirectory.resolve(requestedFile).normalize();
    }

    private boolean isExplicitDoppioPath(Path requestedFile) {
        if (requestedFile.getNameCount() == 0) {
            return false;
        }
        var first = requestedFile.getName(0).toString();
        return ".doppio".equals(first) || "requests".equals(first);
    }

    private boolean isInside(Path child, Path parent) {
        return child.normalize().startsWith(parent.normalize());
    }
}
