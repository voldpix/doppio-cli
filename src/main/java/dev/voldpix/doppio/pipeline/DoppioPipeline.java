package dev.voldpix.doppio.pipeline;

import dev.voldpix.doppio.body.BodyProcessor;
import dev.voldpix.doppio.dsl.DslProcessor;
import dev.voldpix.doppio.expect.ExpectationEvaluator;
import dev.voldpix.doppio.http.HttpTransport;
import dev.voldpix.doppio.http.JavaHttpTransport;
import dev.voldpix.doppio.http.RequestPreparer;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;
import dev.voldpix.doppio.model.PreviewReport;
import dev.voldpix.doppio.model.RunReport;
import dev.voldpix.doppio.seed.SeedFileLoader;
import dev.voldpix.doppio.template.TemplateEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class DoppioPipeline {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final RequestFileResolver requestFileResolver;
    private final SeedFileLoader seedFileLoader;
    private final TemplateEngine templateEngine;
    private final DslProcessor dslProcessor;
    private final BodyProcessor bodyProcessor;
    private final RequestPreparer requestPreparer;
    private final HttpTransport transport;
    private final Duration timeout;
    private final ExpectationEvaluator expectationEvaluator;

    public DoppioPipeline() {
        this(new RequestFileResolver(),
            new SeedFileLoader(),
            new TemplateEngine(),
            new DslProcessor(),
            new BodyProcessor(),
            new RequestPreparer(),
            new JavaHttpTransport(),
            DEFAULT_TIMEOUT);
    }

    public DoppioPipeline(
        RequestFileResolver requestFileResolver,
        SeedFileLoader seedFileLoader,
        TemplateEngine templateEngine,
        DslProcessor dslProcessor,
        BodyProcessor bodyProcessor,
        RequestPreparer requestPreparer,
        HttpTransport transport,
        Duration timeout
    ) {
        this(requestFileResolver,
            seedFileLoader,
            templateEngine,
            dslProcessor,
            bodyProcessor,
            requestPreparer,
            transport,
            timeout,
            new ExpectationEvaluator());
    }

    public DoppioPipeline(
        RequestFileResolver requestFileResolver,
        SeedFileLoader seedFileLoader,
        TemplateEngine templateEngine,
        DslProcessor dslProcessor,
        BodyProcessor bodyProcessor,
        RequestPreparer requestPreparer,
        HttpTransport transport,
        Duration timeout,
        ExpectationEvaluator expectationEvaluator
    ) {
        this.requestFileResolver = requestFileResolver;
        this.seedFileLoader = seedFileLoader;
        this.templateEngine = templateEngine;
        this.dslProcessor = dslProcessor;
        this.bodyProcessor = bodyProcessor;
        this.requestPreparer = requestPreparer;
        this.transport = transport;
        this.timeout = timeout;
        this.expectationEvaluator = expectationEvaluator;
    }

    public RunReport run(Path requestFile, Path workingDirectory, Map<String, String> environment)
        throws DoppioException {
        var preview = preview(requestFile, workingDirectory, environment);
        var response = transport.execute(preview.request(), timeout);
        var expectationReport = expectationEvaluator.evaluate(preview.expectations(), response);

        return new RunReport(preview.requestFile(), preview.request(), response, expectationReport);
    }

    public PreviewReport preview(Path requestFile, Path workingDirectory, Map<String, String> environment)
        throws DoppioException {
        var resolution = requestFileResolver.resolve(requestFile, workingDirectory);
        var rawContent = readRequestFile(resolution.requestFile());
        var metadata = dslProcessor.parseMetadata(rawContent);
        var seedValues = resolution.seedFile() == null
            ? Map.<String, String>of()
            : seedFileLoader.loadIfExists(resolution.seedFile());
        var hydratableContent = removeLocalVariableLines(rawContent);
        var hydratedContent = templateEngine.hydrate(hydratableContent, mergeVariables(environment, seedValues, metadata.variables()));
        var hydratedInspection = dslProcessor.processWithMetadata(hydratedContent);
        var request = hydratedInspection.request();
        var processedBody = bodyProcessor.process(request.body());
        var preparedRequest = requestPreparer.prepare(request, processedBody);
        var bodyKind = request.body() == null ? null : request.body().kind();

        return new PreviewReport(
            resolution.requestFile(),
            preparedRequest,
            bodyKind,
            processedBody,
            hydratedInspection.metadata().expectations()
        );
    }

    private String removeLocalVariableLines(String content) {
        var result = new StringBuilder();
        var inBody = false;

        for (var rawLine : content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            var trimmed = rawLine.trim();
            if (!inBody && isBodyOpen(trimmed)) {
                inBody = true;
            } else if (inBody && "|>".equals(trimmed)) {
                inBody = false;
            }

            if (!inBody && (trimmed.equals("@var") || trimmed.startsWith("@var "))) {
                continue;
            }

            result.append(rawLine).append('\n');
        }

        return result.toString();
    }

    private boolean isBodyOpen(String line) {
        return line.equals("<|")
            || line.equalsIgnoreCase("<json|")
            || line.equalsIgnoreCase("<text|")
            || line.equalsIgnoreCase("<csv|")
            || line.equalsIgnoreCase("<form|");
    }

    private Map<String, String> mergeVariables(
        Map<String, String> environment,
        Map<String, String> seedValues,
        Map<String, String> localVariables
    ) {
        var merged = new LinkedHashMap<String, String>();
        merged.putAll(environment);
        merged.putAll(seedValues);
        merged.putAll(localVariables);
        return merged;
    }

    private String readRequestFile(Path requestFile) throws DoppioException {
        try {
            return Files.readString(requestFile);
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.FILE, "Unable to read request file: " + requestFile, e);
        }
    }
}
