package dev.voldpix.doppio.request;

import dev.voldpix.doppio.dsl.DslProcessor;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;
import dev.voldpix.doppio.pipeline.RequestFileResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RequestFileInspector {
    private final RequestFileResolver resolver;
    private final DslProcessor dslProcessor;

    public RequestFileInspector() {
        this(new RequestFileResolver(), new DslProcessor());
    }

    public RequestFileInspector(RequestFileResolver resolver, DslProcessor dslProcessor) {
        this.resolver = resolver;
        this.dslProcessor = dslProcessor;
    }

    public RequestFileInspection inspect(Path requestedPath, Path workingDirectory) throws DoppioException {
        var resolution = resolver.resolve(requestedPath, workingDirectory);
        var requestFile = resolution.requestFile().toAbsolutePath().normalize();

        try {
            var content = Files.readString(requestFile);
            return new RequestFileInspection(
                requestFile,
                relativePath(resolution.seedFile(), requestFile),
                dslProcessor.inspect(content)
            );
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.FILE, "Unable to read request file: " + requestFile, e);
        }
    }

    private Path relativePath(Path seedFile, Path requestFile) {
        if (seedFile == null) {
            return requestFile.getFileName();
        }

        var requestsDir = seedFile.getParent().resolve("recipes").toAbsolutePath().normalize();
        if (requestFile.startsWith(requestsDir)) {
            return requestsDir.relativize(requestFile);
        }
        return requestFile.getFileName();
    }
}
