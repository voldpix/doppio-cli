package dev.voldpix.doppio.request;

import dev.voldpix.doppio.curl.CurlImport;
import dev.voldpix.doppio.env.DoppioEnvironment;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;
import dev.voldpix.doppio.model.HeaderNames;
import dev.voldpix.doppio.pipeline.DoppioProjectResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RequestFileCreator {
    private static final String ENV_SEED_TEMPLATE = """
        # Seed values override default.seed when selected with --env %s.
        BASE_URL=https://api.%s.example.com
        TOKEN=
        """;

    private final DoppioProjectResolver projectResolver;

    public RequestFileCreator() {
        this(new DoppioProjectResolver());
    }

    public RequestFileCreator(DoppioProjectResolver projectResolver) {
        this.projectResolver = projectResolver;
    }

    public RequestFileCreation create(Path requestedPath, Path workingDirectory) throws DoppioException {
        return create(requestedPath, workingDirectory, RequestGenerationOptions.defaults());
    }

    public RequestFileCreation create(
        Path requestedPath,
        Path workingDirectory,
        RequestGenerationOptions options
    ) throws DoppioException {
        options = options == null ? RequestGenerationOptions.defaults() : options;
        var doppioDir = projectResolver.findDoppioDirectory(workingDirectory.toAbsolutePath().normalize());
        if (doppioDir == null) {
            throw new DoppioException(ErrorKind.FILE, "No .doppio project found. Run `doppio init` first.");
        }
        validateOptions(options);

        var requestsDir = doppioDir.resolve("recipes").toAbsolutePath().normalize();
        var relativePath = normalizeRequestPath(requestedPath);
        var requestFile = requestsDir.resolve(relativePath).normalize();
        if (!requestFile.startsWith(requestsDir)) {
            throw new DoppioException(ErrorKind.FILE, "Request path must stay inside .doppio/recipes: " + requestedPath);
        }
        if (Files.exists(requestFile)) {
            throw new DoppioException(ErrorKind.FILE, "Request already exists: " + relativePath);
        }

        try {
            Files.createDirectories(requestFile.getParent());
            Files.writeString(requestFile, render(relativePath, options));
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.FILE, "Unable to create request: " + relativePath, e);
        }

        return new RequestFileCreation(requestFile, relativePath);
    }

    public RequestFileCreation createFromCurl(
        Path requestedPath,
        Path workingDirectory,
        CurlImport curlImport
    ) throws DoppioException {
        if (curlImport == null) {
            throw new DoppioException(ErrorKind.FILE, "Curl import is required");
        }
        var doppioDir = projectResolver.findDoppioDirectory(workingDirectory.toAbsolutePath().normalize());
        if (doppioDir == null) {
            throw new DoppioException(ErrorKind.FILE, "No .doppio project found. Run `doppio init` first.");
        }
        validateCurlImport(curlImport);

        var requestsDir = doppioDir.resolve("recipes").toAbsolutePath().normalize();
        var relativePath = normalizeRequestPath(requestedPath);
        var requestFile = requestsDir.resolve(relativePath).normalize();
        if (!requestFile.startsWith(requestsDir)) {
            throw new DoppioException(ErrorKind.FILE, "Request path must stay inside .doppio/recipes: " + requestedPath);
        }
        if (Files.exists(requestFile)) {
            throw new DoppioException(ErrorKind.FILE, "Request already exists: " + relativePath);
        }

        try {
            Files.createDirectories(requestFile.getParent());
            Files.writeString(requestFile, renderCurl(relativePath, curlImport));
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.FILE, "Unable to create request: " + relativePath, e);
        }

        return new RequestFileCreation(requestFile, relativePath);
    }

    public RequestFileCreation createEnvironment(String envName, Path workingDirectory) throws DoppioException {
        if (DoppioEnvironment.isDefaultName(envName)) {
            throw new DoppioException(ErrorKind.SEED, "`default` is the built-in seed. Edit .doppio/default.seed instead.");
        }

        var environment = DoppioEnvironment.of(envName);
        if (!environment.selected()) {
            throw new DoppioException(ErrorKind.SEED, "Seed name is required");
        }

        var doppioDir = projectResolver.findDoppioDirectory(workingDirectory.toAbsolutePath().normalize());
        if (doppioDir == null) {
            throw new DoppioException(ErrorKind.FILE, "No .doppio project found. Run `doppio init` first.");
        }

        var envsDir = doppioDir.resolve("seeds").toAbsolutePath().normalize();
        var envFile = envsDir.resolve(environment.fileName()).normalize();
        if (!envFile.startsWith(envsDir)) {
            throw new DoppioException(ErrorKind.SEED, "Seed path must stay inside .doppio/seeds");
        }
        if (Files.exists(envFile)) {
            throw new DoppioException(ErrorKind.SEED, "Seed already exists: " + environment.name());
        }

        try {
            Files.createDirectories(envFile.getParent());
            Files.writeString(envFile, ENV_SEED_TEMPLATE.formatted(environment.name(), environment.name()));
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.SEED, "Unable to create seed: " + environment.name(), e);
        }

        return new RequestFileCreation(envFile, Path.of("seeds").resolve(environment.fileName()));
    }

    private void validateOptions(RequestGenerationOptions options) throws DoppioException {
        for (var header : options.headers()) {
            parseHeader(header);
        }
        for (var queryParam : options.queryParams()) {
            parseQueryParam(queryParam);
        }
    }

    private void validateCurlImport(CurlImport curlImport) throws DoppioException {
        for (var header : curlImport.headers()) {
            parseHeader(header);
        }
        for (var queryParam : curlImport.queryParams()) {
            parseQueryParam(queryParam);
        }
    }

    private String render(Path relativePath, RequestGenerationOptions options) throws DoppioException {
        var builder = new StringBuilder();
        builder.append("@name ").append(displayName(relativePath)).append('\n');
        builder.append(options.method()).append(" {{BASE_URL}}/path").append('\n');

        for (var header : headers(options)) {
            builder.append("-h ").append(header).append('\n');
        }
        for (var queryParam : options.queryParams()) {
            builder.append("-q ").append(parseQueryParam(queryParam)).append('\n');
        }

        var body = bodyBlock(options.bodyKind());
        if (!body.isBlank()) {
            builder.append('\n').append(body);
        }

        return builder.toString();
    }

    private String renderCurl(Path relativePath, CurlImport curlImport) throws DoppioException {
        var builder = new StringBuilder();
        builder.append("@name ").append(displayName(relativePath)).append('\n');
        builder.append(curlImport.method()).append(' ').append(curlImport.url()).append('\n');

        for (var header : curlHeaders(curlImport)) {
            builder.append("-h ").append(header).append('\n');
        }
        for (var queryParam : curlImport.queryParams()) {
            builder.append("-q ").append(parseQueryParam(queryParam)).append('\n');
        }

        var body = curlBodyBlock(curlImport);
        if (!body.isBlank()) {
            builder.append('\n').append(body);
        }

        return builder.toString();
    }

    private List<String> headers(RequestGenerationOptions options) throws DoppioException {
        var headers = new ArrayList<String>();
        var userHeaders = new ArrayList<String>();
        for (var header : options.headers()) {
            userHeaders.add(parseHeader(header));
        }

        var defaultContentType = defaultContentType(options.bodyKind());
        if (defaultContentType != null && userHeaders.stream().noneMatch(this::isContentTypeHeader)) {
            headers.add("Content-Type=" + defaultContentType);
        }

        if (options.bearer()) {
            headers.add("Authorization=Bearer {{TOKEN}}");
        }

        headers.addAll(userHeaders);
        return headers;
    }

    private List<String> curlHeaders(CurlImport curlImport) throws DoppioException {
        var headers = new ArrayList<String>();
        var userHeaders = new ArrayList<String>();
        for (var header : curlImport.headers()) {
            userHeaders.add(parseHeader(header));
        }

        var defaultContentType = defaultContentType(curlImport.bodyKind());
        if (defaultContentType != null && userHeaders.stream().noneMatch(this::isContentTypeHeader)) {
            headers.add("Content-Type=" + defaultContentType);
        }

        headers.addAll(userHeaders);
        return headers;
    }

    private String parseHeader(String header) throws DoppioException {
        if (header == null || header.isBlank()) {
            throw new DoppioException(ErrorKind.FILE, "Header cannot be blank");
        }

        var eqIdx = header.indexOf('=');
        if (eqIdx <= 0) {
            throw new DoppioException(ErrorKind.FILE, "Header must use key=value: " + header);
        }

        var key = header.substring(0, eqIdx).trim();
        var value = header.substring(eqIdx + 1).trim();
        if (key.isBlank()) {
            throw new DoppioException(ErrorKind.FILE, "Header key is missing: " + header);
        }
        if (!HeaderNames.isValid(key)) {
            throw new DoppioException(ErrorKind.FILE, "Invalid HTTP header name: " + key);
        }

        return key + "=" + value;
    }

    private String parseQueryParam(String queryParam) throws DoppioException {
        if (queryParam == null || queryParam.isBlank()) {
            throw new DoppioException(ErrorKind.FILE, "Query param cannot be blank");
        }

        var eqIdx = queryParam.indexOf('=');
        if (eqIdx == -1) {
            var key = queryParam.trim();
            if (key.isBlank()) {
                throw new DoppioException(ErrorKind.FILE, "Query param key is missing: " + queryParam);
            }
            return key;
        }
        if (eqIdx == 0) {
            throw new DoppioException(ErrorKind.FILE, "Query param key is missing: " + queryParam);
        }

        var key = queryParam.substring(0, eqIdx).trim();
        var value = queryParam.substring(eqIdx + 1).trim();
        if (key.isBlank()) {
            throw new DoppioException(ErrorKind.FILE, "Query param key is missing: " + queryParam);
        }
        return key + "=" + value;
    }

    private boolean isContentTypeHeader(String header) {
        var eqIdx = header.indexOf('=');
        var key = eqIdx == -1 ? header : header.substring(0, eqIdx);
        return "content-type".equalsIgnoreCase(key.trim());
    }

    private String defaultContentType(GeneratedBodyKind bodyKind) {
        return switch (bodyKind) {
            case NONE -> null;
            case JSON -> "application/json";
            case TEXT -> "text/plain; charset=utf-8";
            case CSV -> "text/csv; charset=utf-8";
            case FORM -> "application/x-www-form-urlencoded";
        };
    }

    private String bodyBlock(GeneratedBodyKind bodyKind) {
        return switch (bodyKind) {
            case NONE -> "";
            case JSON -> """
                <json|
                {
                  "key": "value"
                }
                |>
                """;
            case TEXT -> """
                <text|
                hello
                |>
                """;
            case CSV -> """
                <csv|
                name,value
                |>
                """;
            case FORM -> """
                <form|
                key=value
                |>
                """;
        };
    }

    private String curlBodyBlock(CurlImport curlImport) {
        if (!curlImport.hasBody()) {
            return "";
        }
        var body = curlImport.body();
        return switch (curlImport.bodyKind()) {
            case NONE -> "";
            case JSON -> "<json|\n" + body + "\n|>\n";
            case TEXT -> "<text|\n" + body + "\n|>\n";
            case CSV -> "<csv|\n" + body + "\n|>\n";
            case FORM -> "<form|\n" + body + "\n|>\n";
        };
    }

    private Path normalizeRequestPath(Path requestedPath) throws DoppioException {
        if (requestedPath == null || requestedPath.toString().isBlank()) {
            throw new DoppioException(ErrorKind.FILE, "Request filename is required");
        }
        if (requestedPath.isAbsolute()) {
            throw new DoppioException(ErrorKind.FILE, "Use a request path relative to .doppio/recipes");
        }

        var path = requestedPath.normalize();
        if (path.getNameCount() == 0 || path.startsWith("..")) {
            throw new DoppioException(ErrorKind.FILE, "Invalid request path: " + requestedPath);
        }

        var first = path.getName(0).toString();
        if (".doppio".equals(first) || "recipes".equals(first)) {
            throw new DoppioException(ErrorKind.FILE, "Use shorthand paths like auth/login.dopo, without .doppio/recipes");
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
