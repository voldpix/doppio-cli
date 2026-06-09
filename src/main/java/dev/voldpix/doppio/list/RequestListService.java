package dev.voldpix.doppio.list;

import dev.voldpix.doppio.dsl.DslParseException;
import dev.voldpix.doppio.dsl.DslProcessor;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;
import dev.voldpix.doppio.pipeline.DoppioProjectResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class RequestListService {
    private final DoppioProjectResolver projectResolver;
    private final DslProcessor dslProcessor;

    public RequestListService() {
        this(new DoppioProjectResolver(), new DslProcessor());
    }

    public RequestListService(DoppioProjectResolver projectResolver, DslProcessor dslProcessor) {
        this.projectResolver = projectResolver;
        this.dslProcessor = dslProcessor;
    }

    public List<RequestListEntry> list(Path workingDirectory) throws DoppioException {
        var doppioDir = projectResolver.findDoppioDirectory(workingDirectory);
        if (doppioDir == null) {
            throw new DoppioException(ErrorKind.FILE, "No .doppio project found");
        }

        var requestsDir = doppioDir.resolve("requests");
        if (!Files.isDirectory(requestsDir)) {
            throw new DoppioException(ErrorKind.FILE, "Requests folder not found: " + requestsDir);
        }

        try (var files = Files.walk(requestsDir)) {
            return files
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".dopo"))
                .sorted(Comparator.comparing(path -> requestsDir.relativize(path).toString()))
                .map(path -> toEntry(requestsDir, path))
                .toList();
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.FILE, "Unable to list requests: " + requestsDir, e);
        }
    }

    private RequestListEntry toEntry(Path requestsDir, Path path) {
        var relativePath = requestsDir.relativize(path);
        try {
            var content = Files.readString(path);
            var metadata = dslProcessor.parseMetadata(content);
            var displayName = metadata.name() == null || metadata.name().isBlank()
                ? fileStem(path)
                : metadata.name();
            return new RequestListEntry(relativePath, displayName, executableParseError(content));
        } catch (DslParseException e) {
            return new RequestListEntry(relativePath, fileStem(path), "metadata parse error");
        } catch (IOException e) {
            return new RequestListEntry(relativePath, fileStem(path), "read error");
        }
    }

    private String executableParseError(String content) {
        if (content.contains("{{")) {
            return null;
        }

        try {
            dslProcessor.process(content);
            return null;
        } catch (DslParseException e) {
            return "parse error";
        }
    }

    private String fileStem(Path path) {
        var filename = path.getFileName().toString();
        var dot = filename.lastIndexOf('.');
        return dot == -1 ? filename : filename.substring(0, dot);
    }
}
