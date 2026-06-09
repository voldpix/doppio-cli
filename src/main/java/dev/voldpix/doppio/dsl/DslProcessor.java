package dev.voldpix.doppio.dsl;

import dev.voldpix.doppio.model.DoppioRequest;
import dev.voldpix.doppio.model.Header;
import dev.voldpix.doppio.model.HttpMethod;
import dev.voldpix.doppio.model.QueryParam;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class DslProcessor {
    private static final String BODY_OPEN = "<|";
    private static final String BODY_CLOSE = "|>";

    public DoppioRequest process(String content) throws DslParseException {
        var blocks = extractBlocks(content);
        var errors = new ArrayList<ParseError>();

        if (blocks.directives().isEmpty()) {
            errors.add(new ParseError("empty file", "file must contain a request"));
            throw new DslParseException(errors);
        }

        var requestLine = parseRequestLine(blocks.directives().getFirst(), errors);
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
            requestLine.method(),
            requestLine.url(),
            headers,
            queryParams,
            blocks.body()
        );
    }

    private RawBlocks extractBlocks(String content) throws DslParseException {
        if (content == null || content.isBlank()) {
            throw new DslParseException(List.of(new ParseError("empty file", "file must contain a request")));
        }

        var directives = new ArrayList<String>();
        var bodyLines = new ArrayList<String>();
        var errors = new ArrayList<ParseError>();
        var inBody = false;
        var openCount = 0;
        var closeCount = 0;

        for (var rawLine : content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            var trimmed = rawLine.trim();

            if (BODY_OPEN.equals(trimmed)) {
                openCount++;
                if (inBody) {
                    errors.add(new ParseError(BODY_OPEN, "<| appears more than once"));
                }
                inBody = true;
                continue;
            }

            if (BODY_CLOSE.equals(trimmed)) {
                closeCount++;
                if (!inBody) {
                    errors.add(new ParseError(BODY_CLOSE, "|> found with no opening <|"));
                }
                inBody = false;
                continue;
            }

            if (inBody) {
                bodyLines.add(rawLine);
                continue;
            }

            if (isIgnoredLine(trimmed)) {
                continue;
            }

            directives.add(normalizeDirectiveLine(rawLine));
        }

        if (openCount > 1) {
            errors.add(new ParseError(BODY_OPEN, "<| appears more than once"));
        }
        if (closeCount > 1) {
            errors.add(new ParseError(BODY_CLOSE, "|> appears more than once"));
        }
        if (openCount == 1 && closeCount == 0) {
            errors.add(new ParseError(BODY_OPEN, "<| opened but never closed with |>"));
        }
        if (openCount == 0 && closeCount == 1) {
            errors.add(new ParseError(BODY_CLOSE, "|> found with no opening <|"));
        }

        if (!errors.isEmpty()) {
            throw new DslParseException(errors);
        }

        var body = openCount == 0 ? null : trimOuterBlankLines(bodyLines);
        if (openCount == 1 && body.isBlank()) {
            throw new DslParseException(List.of(new ParseError("<| ... |>", "body block is empty")));
        }

        return new RawBlocks(directives, openCount == 0 ? null : body);
    }

    private boolean isIgnoredLine(String trimmed) {
        return trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("--");
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

    private RequestLine parseRequestLine(String line, List<ParseError> errors) {
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
        if (method.isPresent() && !isValidHttpUrl(url)) {
            errors.add(new ParseError(line, "invalid URL. Ensure it includes http:// or https://"));
        }

        return new RequestLine(method.orElse(HttpMethod.GET), url);
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

    private record RawBlocks(List<String> directives, String body) {
    }

    private record RequestLine(HttpMethod method, String url) {
    }
}
