package dev.voldpix.doppio.pipeline;

import dev.voldpix.doppio.dsl.DslProcessor;
import dev.voldpix.doppio.http.HttpTransport;
import dev.voldpix.doppio.http.JavaHttpTransport;
import dev.voldpix.doppio.http.RequestPreparer;
import dev.voldpix.doppio.json.JsonBodyProcessor;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;
import dev.voldpix.doppio.model.RunReport;
import dev.voldpix.doppio.seed.SeedFileLoader;
import dev.voldpix.doppio.template.TemplateEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

public class DoppioPipeline {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final RequestFileResolver requestFileResolver;
    private final SeedFileLoader seedFileLoader;
    private final TemplateEngine templateEngine;
    private final DslProcessor dslProcessor;
    private final JsonBodyProcessor jsonBodyProcessor;
    private final RequestPreparer requestPreparer;
    private final HttpTransport transport;
    private final Duration timeout;

    public DoppioPipeline() {
        this(new RequestFileResolver(),
            new SeedFileLoader(),
            new TemplateEngine(),
            new DslProcessor(),
            new JsonBodyProcessor(),
            new RequestPreparer(),
            new JavaHttpTransport(),
            DEFAULT_TIMEOUT);
    }

    public DoppioPipeline(
        RequestFileResolver requestFileResolver,
        SeedFileLoader seedFileLoader,
        TemplateEngine templateEngine,
        DslProcessor dslProcessor,
        JsonBodyProcessor jsonBodyProcessor,
        RequestPreparer requestPreparer,
        HttpTransport transport,
        Duration timeout
    ) {
        this.requestFileResolver = requestFileResolver;
        this.seedFileLoader = seedFileLoader;
        this.templateEngine = templateEngine;
        this.dslProcessor = dslProcessor;
        this.jsonBodyProcessor = jsonBodyProcessor;
        this.requestPreparer = requestPreparer;
        this.transport = transport;
        this.timeout = timeout;
    }

    public RunReport run(Path requestFile, Path workingDirectory, Map<String, String> environment)
        throws DoppioException {
        var resolution = requestFileResolver.resolve(requestFile, workingDirectory);
        var seedValues = resolution.seedFile() == null
            ? Map.<String, String>of()
            : seedFileLoader.loadIfExists(resolution.seedFile());
        var rawContent = readRequestFile(resolution.requestFile());
        var hydratedContent = templateEngine.hydrate(rawContent, seedValues, environment);
        var request = dslProcessor.process(hydratedContent);
        var processedBody = jsonBodyProcessor.process(request.body());
        var preparedRequest = requestPreparer.prepare(request.withBody(processedBody));
        var response = transport.execute(preparedRequest, timeout);

        return new RunReport(preparedRequest, response);
    }

    private String readRequestFile(Path requestFile) throws DoppioException {
        try {
            return Files.readString(requestFile);
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.FILE, "Unable to read request file: " + requestFile, e);
        }
    }
}
