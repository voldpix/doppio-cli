package dev.voldpix.doppio.dsl;

import dev.voldpix.doppio.expect.Expectation;
import dev.voldpix.doppio.expect.ExpectationKind;
import dev.voldpix.doppio.model.BodyBlock;
import dev.voldpix.doppio.model.BodyKind;
import dev.voldpix.doppio.model.DoppioRequest;
import dev.voldpix.doppio.model.Header;
import dev.voldpix.doppio.model.HttpMethod;
import dev.voldpix.doppio.model.QueryParam;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class DslProcessor {
    private static final String BODY_CLOSE = "|>";
    private static final Pattern BODY_OPEN = Pattern.compile("^<(?:(json|text|csv|form))?\\|$", Pattern.CASE_INSENSITIVE);

    public DslMetadata parseMetadata(String content) throws DslParseException {
        return extractBlocks(content, false).metadata();
    }

    public DslInspection inspect(String content) throws DslParseException {
        var blocks = extractBlocks(content, true);
        return new DslInspection(blocks.metadata(), parseRequest(blocks, true));
    }

    public DslInspection processWithMetadata(String content) throws DslParseException {
        var blocks = extractBlocks(content, true);
        return new DslInspection(blocks.metadata(), parseRequest(blocks, false));
    }

    public DoppioRequest process(String content) throws DslParseException {
        return processWithMetadata(content).request();
    }

    private DoppioRequest parseRequest(RawBlocks blocks, boolean allowTemplatedUrl) throws DslParseException {
        var errors = new ArrayList<ParseError>();

        if (blocks.directives().isEmpty()) {
            errors.add(new ParseError("empty file", "file must contain a request"));
            throw new DslParseException(errors);
        }

        var requestLine = parseRequestLine(blocks.directives().getFirst(), errors, allowTemplatedUrl);
        var headers = new ArrayList<Header>();
        var queryParams = new ArrayList<QueryParam>();

        for (var line : blocks.directives().subList(1, blocks.directives().size())) {
            if (line.equals("-h") || line.startsWith("-h ") || line.equals("header") || line.startsWith("header ")) {
                parseHeader(line, headers, errors);
            } else if (line.equals("-q") || line.startsWith("-q ") || line.equals("query") || line.startsWith("query ")) {
                parseQueryParam(line, queryParams, errors);
            } else {
                errors.add(new ParseError(line, "unrecognized directive"));
            }
        }

        if (!errors.isEmpty()) {
            throw new DslParseException(errors);
        }

        return new DoppioRequest(
            blocks.metadata().name(),
            requestLine.method(),
            requestLine.url(),
            headers,
            queryParams,
            blocks.body()
        );
    }

    private RawBlocks extractBlocks(String content, boolean requireRequest) throws DslParseException {
        if (content == null || content.isBlank()) {
            throw new DslParseException(List.of(new ParseError("empty file", "file must contain a request")));
        }

        var metadata = new MetadataBuilder();
        var directives = new ArrayList<String>();
        var bodyLines = new ArrayList<String>();
        var errors = new ArrayList<ParseError>();
        var inBody = false;
        var requestSeen = false;
        var bodyKind = BodyKind.JSON;
        var openCount = 0;
        var closeCount = 0;

        for (var rawLine : content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            var trimmed = rawLine.trim();

            if (inBody) {
                if (BODY_CLOSE.equals(trimmed)) {
                    closeCount++;
                    inBody = false;
                } else if (!trimmed.startsWith("#")) {
                    bodyLines.add(rawLine);
                }
                continue;
            }

            var bodyOpen = parseBodyOpen(trimmed);
            if (bodyOpen.isPresent()) {
                openCount++;
                if (openCount > 1) {
                    errors.add(new ParseError(trimmed, "only one body block is allowed"));
                }
                bodyKind = bodyOpen.get();
                inBody = true;
                continue;
            }

            if (BODY_CLOSE.equals(trimmed)) {
                closeCount++;
                errors.add(new ParseError(BODY_CLOSE, "|> found with no opening body block"));
                continue;
            }

            if (isIgnoredLine(trimmed)) {
                continue;
            }

            var line = normalizeDirectiveLine(rawLine);
            if (line.startsWith("@")) {
                if (requestSeen) {
                    errors.add(new ParseError(line, "@ metadata must appear before the request line"));
                } else {
                    parseMetadataLine(line, metadata, errors);
                }
                continue;
            }

            requestSeen = true;
            directives.add(line);
        }

        if (inBody) {
            errors.add(new ParseError("<|", "body block opened but never closed with |>"));
        }
        if (openCount > 1) {
            errors.add(new ParseError("<|", "only one body block is allowed"));
        }
        if (closeCount > 1) {
            errors.add(new ParseError(BODY_CLOSE, "|> appears more than once"));
        }

        if (!errors.isEmpty()) {
            throw new DslParseException(errors);
        }

        if (requireRequest && directives.isEmpty()) {
            throw new DslParseException(List.of(new ParseError("empty file", "file must contain a request")));
        }

        BodyBlock body = null;
        if (openCount == 1) {
            var contentBody = trimOuterBlankLines(bodyLines);
            if (contentBody.isBlank()) {
                throw new DslParseException(List.of(new ParseError("<| ... |>", "body block is empty")));
            }
            body = new BodyBlock(bodyKind, contentBody);
        }

        return new RawBlocks(metadata.build(), directives, body);
    }

    private Optional<BodyKind> parseBodyOpen(String line) throws DslParseException {
        if (!line.startsWith("<")) {
            return Optional.empty();
        }

        var matcher = BODY_OPEN.matcher(line);
        if (!matcher.matches()) {
            if (line.startsWith("<file")) {
                throw new DslParseException(List.of(new ParseError(line, "<file path=...> body blocks are reserved for a later release")));
            }
            return Optional.empty();
        }

        var type = matcher.group(1);
        var kind = BodyKind.parse(type);
        if (kind.isEmpty()) {
            throw new DslParseException(List.of(new ParseError(line, "unsupported body type")));
        }
        return kind;
    }

    private boolean isIgnoredLine(String trimmed) {
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    private String normalizeDirectiveLine(String line) {
        return line.trim().replaceAll("\\s+", " ");
    }

    private String trimOuterBlankLines(List<String> lines) {
        var start = 0;
        var end = lines.size();

        while (start < end && lines.get(start).isBlank()) {
            start++;
        }
        while (end > start && lines.get(end - 1).isBlank()) {
            end--;
        }

        return String.join("\n", lines.subList(start, end)).strip();
    }

    private void parseMetadataLine(String line, MetadataBuilder metadata, List<ParseError> errors) {
        if (line.startsWith("@name")) {
            var name = line.replaceFirst("^@name(\\s+|$)", "").trim();
            if (name.isBlank()) {
                errors.add(new ParseError(line, "expected: @name <request name>"));
                return;
            }
            metadata.setName(name, line, errors);
        } else if (line.startsWith("@var")) {
            var variable = line.replaceFirst("^@var(\\s+|$)", "").trim();
            parseLocalVariable(line, variable, metadata, errors);
        } else if (line.startsWith("@expect")) {
            var expectation = line.replaceFirst("^@expect(\\s+|$)", "").trim();
            parseExpectation(line, expectation, metadata, errors);
        } else {
            errors.add(new ParseError(line, "unknown metadata directive"));
        }
    }

    private void parseExpectation(String line, String expectation, MetadataBuilder metadata, List<ParseError> errors) {
        if (expectation.isBlank()) {
            errors.add(new ParseError(line, "expected: @expect status=200, @expect header <name> contains <text>, or @expect body contains <text>"));
            return;
        }

        if (expectation.equals("status") || expectation.startsWith("status=") || expectation.startsWith("status ")) {
            parseStatusExpectation(line, expectation, metadata, errors);
            return;
        }

        var headerMatcher = Pattern.compile("^header\\s+(\\S+)\\s+contains\\s+(.+)$", Pattern.CASE_INSENSITIVE)
            .matcher(expectation);
        if (headerMatcher.matches()) {
            metadata.addExpectation(new Expectation(
                ExpectationKind.HEADER_CONTAINS,
                headerMatcher.group(1).trim(),
                stripMatchingQuotes(headerMatcher.group(2).trim()),
                line
            ));
            return;
        }

        var bodyMatcher = Pattern.compile("^body\\s+contains\\s+(.+)$", Pattern.CASE_INSENSITIVE)
            .matcher(expectation);
        if (bodyMatcher.matches()) {
            metadata.addExpectation(new Expectation(
                ExpectationKind.BODY_CONTAINS,
                "body",
                stripMatchingQuotes(bodyMatcher.group(1).trim()),
                line
            ));
            return;
        }

        errors.add(new ParseError(line, "expected: @expect status=200, @expect header <name> contains <text>, or @expect body contains <text>"));
    }

    private void parseStatusExpectation(String line, String expectation, MetadataBuilder metadata, List<ParseError> errors) {
        var expected = expectation.replaceFirst("^status(\\s+|=|$)", "").trim();
        if (expected.isBlank()) {
            errors.add(new ParseError(line, "expected: @expect status=200"));
            return;
        }
        try {
            Integer.parseInt(expected);
        } catch (NumberFormatException e) {
            errors.add(new ParseError(line, "expected numeric HTTP status"));
            return;
        }
        metadata.addExpectation(new Expectation(ExpectationKind.STATUS, "status", expected, line));
    }

    private void parseLocalVariable(String line, String variable, MetadataBuilder metadata, List<ParseError> errors) {
        var eqIdx = variable.indexOf('=');
        if (eqIdx <= 0) {
            errors.add(new ParseError(line, "expected: @var KEY=value"));
            return;
        }

        var key = variable.substring(0, eqIdx).trim();
        var value = stripMatchingQuotes(variable.substring(eqIdx + 1).trim());
        if (key.isBlank()) {
            errors.add(new ParseError(line, "variable key is missing"));
            return;
        }
        if (key.chars().anyMatch(Character::isWhitespace)) {
            errors.add(new ParseError(line, "variable key cannot contain whitespace"));
            return;
        }
        metadata.addVariable(key, value, line, errors);
    }

    private String stripMatchingQuotes(String value) {
        if (value.length() < 2) {
            return value;
        }

        var first = value.charAt(0);
        var last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private RequestLine parseRequestLine(String line, List<ParseError> errors, boolean allowTemplatedUrl) {
        var parts = line.split("\\s+", 2);
        if (parts.length < 2) {
            errors.add(new ParseError(line, "expected: http method and URL e.g. GET https://api.example.com"));
            return new RequestLine(HttpMethod.GET, "");
        }

        var method = HttpMethod.parse(parts[0]);
        if (method.isEmpty()) {
            errors.add(new ParseError(line, "expected: http method e.g. GET"));
        }

        var url = parts[1].trim();
        if (method.isPresent() && !isValidHttpUrl(url) && !(allowTemplatedUrl && hasTemplate(url))) {
            errors.add(new ParseError(line, "invalid URL. Ensure it includes http:// or https://"));
        }

        return new RequestLine(method.orElse(HttpMethod.GET), url);
    }

    private boolean hasTemplate(String value) {
        return value.contains("{{") && value.contains("}}");
    }

    private boolean isValidHttpUrl(String url) {
        try {
            var uri = new URI(url);
            var scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private void parseHeader(String line, List<Header> headers, List<ParseError> errors) {
        var stripped = line.replaceFirst("^(-h|header)(\\s+|$)", "").trim();
        var eqIdx = stripped.indexOf('=');

        if (eqIdx <= 0) {
            errors.add(new ParseError(line, "expected: -h <key>=<value> e.g. -h Content-Type=application/json"));
            return;
        }

        headers.add(new Header(stripped.substring(0, eqIdx).trim(), stripped.substring(eqIdx + 1).trim()));
    }

    private void parseQueryParam(String line, List<QueryParam> queryParams, List<ParseError> errors) {
        var stripped = line.replaceFirst("^(-q|query)(\\s+|$)", "").trim();
        if (stripped.isEmpty()) {
            errors.add(new ParseError(line, "key is missing. expected: -q <key>=<value> e.g. -q page=1"));
            return;
        }

        var eqIdx = stripped.indexOf('=');
        if (eqIdx == -1) {
            queryParams.add(new QueryParam(stripped, ""));
        } else if (eqIdx == 0) {
            errors.add(new ParseError(line, "key is missing. expected: -q <key>=<value> e.g. -q page=1"));
        } else {
            queryParams.add(new QueryParam(
                stripped.substring(0, eqIdx).trim(),
                stripped.substring(eqIdx + 1).trim()
            ));
        }
    }

    private record RawBlocks(DslMetadata metadata, List<String> directives, BodyBlock body) {
    }

    private record RequestLine(HttpMethod method, String url) {
    }

    private static final class MetadataBuilder {
        private String name;
        private final Map<String, String> variables = new LinkedHashMap<>();
        private final List<Expectation> expectations = new ArrayList<>();

        private void setName(String value, String line, List<ParseError> errors) {
            if (name != null) {
                errors.add(new ParseError(line, "only one @name is allowed"));
                return;
            }
            name = value;
        }

        private void addVariable(String key, String value, String line, List<ParseError> errors) {
            if (variables.containsKey(key)) {
                errors.add(new ParseError(line, "duplicate local variable: " + key));
                return;
            }
            variables.put(key, value);
        }

        private void addExpectation(Expectation expectation) {
            expectations.add(expectation);
        }

        private DslMetadata build() {
            return new DslMetadata(name, variables, expectations);
        }
    }
}
