package dev.voldpix.doppio.pipeline;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;

import java.nio.file.Files;
import java.nio.file.Path;

public class RequestFileResolver {
    public RequestResolution resolve(Path requestedFile, Path workingDirectory) throws DoppioException {
        if (!requestedFile.getFileName().toString().endsWith(".dopo")) {
            throw new DoppioException(ErrorKind.FILE, "Only .dopo request files are supported: " + requestedFile);
        }

        var normalizedWorkingDirectory = workingDirectory.toAbsolutePath().normalize();
        var directCandidate = normalize(normalizedWorkingDirectory, requestedFile);
        var doppioDir = findDoppioDirectory(normalizedWorkingDirectory);

        if (requestedFile.isAbsolute()) {
            return existingDirectFile(directCandidate, doppioDir);
        }

        if (doppioDir == null) {
            return existingStandaloneFile(directCandidate, requestedFile);
        }

        var seedFile = doppioDir.resolve("local.seed");
        if (isInside(normalizedWorkingDirectory, doppioDir) && Files.exists(directCandidate)) {
            return existingFile(directCandidate, seedFile);
        }

        if (isExplicitDoppioPath(requestedFile) && Files.exists(directCandidate)) {
            return existingFile(directCandidate, seedFile);
        }

        var requestsCandidate = doppioDir.resolve("requests").resolve(requestedFile).normalize();
        if (Files.exists(requestsCandidate)) {
            return existingFile(requestsCandidate, seedFile);
        }

        throw new DoppioException(
            ErrorKind.FILE,
            "Request file not found in .doppio/requests: " + requestedFile
        );
    }

    private RequestResolution existingDirectFile(Path directCandidate, Path doppioDir) throws DoppioException {
        var seedFile = doppioDir == null ? null : doppioDir.resolve("local.seed");
        return existingFile(directCandidate, seedFile);
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

    private Path findDoppioDirectory(Path workingDirectory) {
        var current = workingDirectory;
        while (current != null) {
            if (isDoppioDirectory(current)) {
                return current;
            }

            var childDoppio = current.resolve(".doppio");
            if (isDoppioDirectory(childDoppio)) {
                return childDoppio;
            }

            current = current.getParent();
        }
        return null;
    }

    private boolean isDoppioDirectory(Path path) {
        return path != null
            && ".doppio".equals(path.getFileName() == null ? "" : path.getFileName().toString())
            && Files.isDirectory(path)
            && (Files.exists(path.resolve("local.seed")) || Files.isDirectory(path.resolve("requests")));
    }

    private boolean isInside(Path child, Path parent) {
        return child.normalize().startsWith(parent.normalize());
    }
}
