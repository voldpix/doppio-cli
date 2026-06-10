package dev.voldpix.doppio.request;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;
import dev.voldpix.doppio.pipeline.DoppioProjectResolver;
import dev.voldpix.doppio.pipeline.RequestFileResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.format.DateTimeFormatter;

public class RequestFileRemover {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final DoppioProjectResolver projectResolver;
    private final RequestFileResolver requestFileResolver;
    private final Clock clock;

    public RequestFileRemover() {
        this(new DoppioProjectResolver(), new RequestFileResolver(), Clock.systemDefaultZone());
    }

    public RequestFileRemover(DoppioProjectResolver projectResolver, RequestFileResolver requestFileResolver, Clock clock) {
        this.projectResolver = projectResolver;
        this.requestFileResolver = requestFileResolver;
        this.clock = clock;
    }

    public RemovedRequest remove(Path requestedPath, Path workingDirectory) throws DoppioException {
        var doppioDir = projectResolver.findDoppioDirectory(workingDirectory.toAbsolutePath().normalize());
        if (doppioDir == null) {
            throw new DoppioException(ErrorKind.FILE, "No .doppio project found");
        }

        var requestsDir = doppioDir.resolve("requests").toAbsolutePath().normalize();
        var directCandidate = normalize(workingDirectory.toAbsolutePath().normalize(), requestedPath);
        if (Files.exists(directCandidate) && !directCandidate.startsWith(requestsDir)) {
            throw new DoppioException(ErrorKind.FILE, "rm only removes files under .doppio/requests");
        }

        var resolution = requestFileResolver.resolve(requestedPath, workingDirectory);
        var requestFile = resolution.requestFile().toAbsolutePath().normalize();
        if (!requestFile.startsWith(requestsDir)) {
            throw new DoppioException(ErrorKind.FILE, "rm only removes files under .doppio/requests");
        }

        var relativePath = requestsDir.relativize(requestFile);
        var trashFile = uniqueTrashPath(doppioDir.resolve("trash"), relativePath);

        try {
            Files.createDirectories(trashFile.getParent());
            Files.move(requestFile, trashFile);
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.FILE, "Unable to move request to trash: " + relativePath, e);
        }

        return new RemovedRequest(relativePath, trashFile);
    }

    private Path normalize(Path workingDirectory, Path requestedPath) {
        var candidate = requestedPath.isAbsolute()
            ? requestedPath.normalize()
            : workingDirectory.resolve(requestedPath).normalize();
        var fileName = candidate.getFileName();
        if (fileName == null) {
            return candidate;
        }

        var fileNameText = fileName.toString();
        if (!fileNameText.endsWith(".dopo") && !fileNameText.contains(".")) {
            return candidate.resolveSibling(fileNameText + ".dopo");
        }
        return candidate;
    }

    private Path uniqueTrashPath(Path trashDir, Path relativePath) {
        var base = STAMP.format(clock.instant().atZone(clock.getZone())) + "-" + relativePath.toString().replaceAll("[/\\\\]+", "-");
        var candidate = trashDir.resolve(base);
        var index = 1;
        while (Files.exists(candidate)) {
            candidate = trashDir.resolve(base + "." + index);
            index++;
        }
        return candidate;
    }
}
