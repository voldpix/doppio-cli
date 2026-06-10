package dev.voldpix.doppio.cli;

import dev.voldpix.doppio.curl.CurlImportParser;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;
import dev.voldpix.doppio.model.HttpMethod;
import dev.voldpix.doppio.request.GeneratedBodyKind;
import dev.voldpix.doppio.request.RequestFileCreator;
import dev.voldpix.doppio.request.RequestGenerationOptions;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "gen", mixinStandardHelpOptions = true, description = "Create a request file under .doppio/requests.")
public class GenCommand implements Callable<Integer> {
    @Parameters(index = "0", paramLabel = "FILE", description = "Request path to create, with or without .dopo.")
    private Path file;

    @Option(names = "--method", paramLabel = "METHOD", description = "HTTP method: GET, POST, PUT, PATCH, DELETE.")
    private String method;

    @Option(names = "--body", paramLabel = "KIND", description = "Body kind: none, json, text, csv, form.")
    private String body;

    @Option(names = "--bearer", description = "Add Authorization=Bearer {{TOKEN}} header.")
    private boolean bearer;

    @Option(names = {"-H", "--header"}, paramLabel = "KEY=VALUE", description = "Header to add to the generated request.")
    private List<String> headers = new ArrayList<>();

    @Option(names = {"-q", "--query"}, paramLabel = "KEY[=VALUE]", description = "Query parameter to add to the generated request.")
    private List<String> queryParams = new ArrayList<>();

    @Option(names = "--from-curl", paramLabel = "CURL", description = "Create a request from a basic curl command.")
    private String fromCurl;

    private final Path workingDirectory;
    private final RequestFileCreator creator;
    private final CurlImportParser curlImportParser;
    private final PrintWriter out;
    private final PrintWriter err;

    public GenCommand(Path workingDirectory, RequestFileCreator creator, PrintWriter out, PrintWriter err) {
        this(workingDirectory, creator, new CurlImportParser(), out, err);
    }

    public GenCommand(
        Path workingDirectory,
        RequestFileCreator creator,
        CurlImportParser curlImportParser,
        PrintWriter out,
        PrintWriter err
    ) {
        this.workingDirectory = workingDirectory;
        this.creator = creator;
        this.curlImportParser = curlImportParser;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() {
        try {
            var created = fromCurl == null
                ? creator.create(file, workingDirectory, options())
                : creator.createFromCurl(file, workingDirectory, curlImport());
            out.println("Created request");
            out.println("  File: " + created.relativePath());
            out.println("  Path: " + created.requestFile());
            out.flush();
            return 0;
        } catch (DoppioException e) {
            err.println("Generate Error: " + e.getMessage());
            err.flush();
            return 1;
        }
    }

    private RequestGenerationOptions options() throws DoppioException {
        var parsedMethod = method == null
            ? HttpMethod.POST
            : HttpMethod.parse(method)
                .orElseThrow(() -> new DoppioException(ErrorKind.FILE, "Unsupported method: " + method));
        var parsedBody = body == null
            ? null
            : GeneratedBodyKind.parse(body)
                .orElseThrow(() -> new DoppioException(ErrorKind.FILE, "Unsupported body kind: " + body));

        return new RequestGenerationOptions(parsedMethod, parsedBody, bearer, headers, queryParams);
    }

    private dev.voldpix.doppio.curl.CurlImport curlImport() throws DoppioException {
        if (method != null || body != null || bearer || !headers.isEmpty() || !queryParams.isEmpty()) {
            throw new DoppioException(ErrorKind.FILE, "--from-curl cannot be combined with --method, --body, --bearer, -H, or -q");
        }
        return curlImportParser.parse(fromCurl);
    }
}
