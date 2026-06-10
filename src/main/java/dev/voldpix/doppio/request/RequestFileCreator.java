package dev.voldpix.doppio.request;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;
import dev.voldpix.doppio.pipeline.DoppioProjectResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class RequestFileCreator {
    private static final String TEMPLATE = """
        @name %s
        POST {{BASE_URL}}/path
        -h Content-Type=application/json

        <json|
        {
          "key": "value"
        }
        |>
        """;

    private final DoppioProjectResolver projectResolver;

    public RequestFileCreator() {
        this(new DoppioProjectResolver());
    }

    public RequestFileCreator(DoppioProjectResolver projectResolver) {
        this.projectResolver = projectResolver;
    }

    public RequestFileCreation create(Path requestedPath, Path workingDirectory) throws DoppioException {
        var doppioDir = projectResolver.findDoppioDirectory(workingDirectory.toAbsolutePath().normalize());
        if (doppioDir == null) {
            throw new DoppioException(ErrorKind.FILE, "No .doppio project found. Run `doppio init` first.");
        }

        var requestsDir = doppioDir.resolve("requests").toAbsolutePath().normalize();
        var relativePath = normalizeRequestPath(requestedPath);
        var requestFile = requestsDir.resolve(relativePath).normalize();
        if (!requestFile.startsWith(requestsDir)) {
            throw new DoppioException(ErrorKind.FILE, "Request path must stay inside .doppio/requests: " + requestedPath);
        }
        if (Files.exists(requestFile)) {
            throw new DoppioException(ErrorKind.FILE, "Request already exists: " + relativePath);
        }

        try {
            Files.createDirectories(requestFile.getParent());
            Files.writeString(requestFile, TEMPLATE.formatted(displayName(relativePath)));
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.FILE, "Unable to create request: " + relativePath, e);
        }

        return new RequestFileCreation(requestFile, relativePath);
    }

    private Path normalizeRequestPath(Path requestedPath) throws DoppioException {
        if (requestedPath == null || requestedPath.toString().isBlank()) {
            throw new DoppioException(ErrorKind.FILE, "Request filename is required");
        }
        if (requestedPath.isAbsolute()) {
            throw new DoppioException(ErrorKind.FILE, "Use a request path relative to .doppio/requests");
        }

        var path = requestedPath.normalize();
        if (path.getNameCount() == 0 || path.startsWith("..")) {
            throw new DoppioException(ErrorKind.FILE, "Invalid request path: " + requestedPath);
        }

        var first = path.getName(0).toString();
        if (".doppio".equals(first) || "requests".equals(first)) {
            throw new DoppioException(ErrorKind.FILE, "Use shorthand paths like auth/login.dopo, without .doppio/requests");
        }

        var filename = path.getFileName().toString();
        if (filename.endsWith(".dopo")) {
            return path;
        }
        if (filename.contains(".")) {
            throw new DoppioException(ErrorKind.FILE, "Request files must use the .dopo extension: " + requestedPath);
        }

        return path.resolveSibling(filename + ".dopo");
    }

    private String displayName(Path relativePath) {
        var filename = relativePath.getFileName().toString();
        var dot = filename.lastIndexOf('.');
        var stem = dot == -1 ? filename : filename.substring(0, dot);
        var words = stem.replaceAll("[-_]+", " ").trim().split("\\s+");
        var result = new StringBuilder();
        for (var word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            if (word.length() > 1) {
                result.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return result.isEmpty() ? "New request" : result.toString();
    }
}
