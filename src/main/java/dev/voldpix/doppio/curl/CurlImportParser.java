package dev.voldpix.doppio.curl;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;
import dev.voldpix.doppio.model.HttpMethod;
import dev.voldpix.doppio.request.GeneratedBodyKind;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CurlImportParser {
    private static final Set<String> IGNORED_FLAGS = Set.of(
        "-s",
        "--silent",
        "-S",
        "--show-error",
        "-L",
        "--location",
        "-k",
        "--insecure",
        "--compressed"
    );

    public CurlImport parse(String command) throws DoppioException {
        var tokens = tokenize(command);
        if (tokens.isEmpty() || !"curl".equals(tokens.getFirst())) {
            throw error("Expected a curl command");
        }

        HttpMethod method = null;
        String url = null;
        var headers = new ArrayList<String>();
        var dataParts = new ArrayList<String>();

        for (var index = 1; index < tokens.size(); index++) {
            var token = tokens.get(index);

            if (IGNORED_FLAGS.contains(token)) {
                continue;
            }

            if (token.equals("-X") || token.equals("--request")) {
                method = parseMethod(requireValue(tokens, ++index, token));
            } else if (token.startsWith("-X") && token.length() > 2) {
                method = parseMethod(token.substring(2));
            } else if (token.startsWith("--request=")) {
                method = parseMethod(token.substring("--request=".length()));
            } else if (token.equals("-H") || token.equals("--header")) {
                headers.add(parseHeader(requireValue(tokens, ++index, token)));
            } else if (token.startsWith("-H") && token.length() > 2) {
                headers.add(parseHeader(token.substring(2)));
            } else if (token.startsWith("--header=")) {
                headers.add(parseHeader(token.substring("--header=".length())));
            } else if (isDataOption(token)) {
                dataParts.add(requireValue(tokens, ++index, token));
            } else if (isInlineDataOption(token)) {
                dataParts.add(token.substring(token.indexOf('=') + 1));
            } else if (token.startsWith("-d") && token.length() > 2) {
                dataParts.add(token.substring(2));
            } else if (token.equals("--url")) {
                url = requireValue(tokens, ++index, token);
            } else if (token.startsWith("--url=")) {
                url = token.substring("--url=".length());
            } else if (token.startsWith("-")) {
                throw error("Unsupported curl option: " + token);
            } else if (url == null) {
                url = token;
            } else {
                throw error("Unexpected curl argument: " + token);
            }
        }

        if (url == null || url.isBlank()) {
            throw error("Curl command must include a URL");
        }

        var parsedUrl = parseUrl(url);
        var body = body(dataParts);
        var bodyKind = detectBodyKind(headers, body, dataParts.size());
        if (method == null) {
            method = bodyKind == GeneratedBodyKind.NONE ? HttpMethod.GET : HttpMethod.POST;
        }

        return new CurlImport(method, parsedUrl.baseUrl(), headers, parsedUrl.queryParams(), bodyKind, renderBody(bodyKind, body));
    }

    private boolean isDataOption(String token) {
        return token.equals("-d")
            || token.equals("--data")
            || token.equals("--data-raw")
            || token.equals("--data-binary");
    }

    private boolean isInlineDataOption(String token) {
        return token.startsWith("--data=")
            || token.startsWith("--data-raw=")
            || token.startsWith("--data-binary=");
    }

    private String body(List<String> dataParts) throws DoppioException {
        if (dataParts.isEmpty()) {
            return null;
        }
        return String.join("&", dataParts);
    }

    private GeneratedBodyKind detectBodyKind(List<String> headers, String body, int dataPartCount) throws DoppioException {
        if (body == null || body.isBlank()) {
            return GeneratedBodyKind.NONE;
        }

        var contentType = headers.stream()
            .filter(header -> header.regionMatches(true, 0, "Content-Type=", 0, "Content-Type=".length()))
            .map(header -> header.substring("Content-Type=".length()).toLowerCase(Locale.ROOT))
            .findFirst()
            .orElse("");
        var trimmed = body.trim();

        if (contentType.contains("json") || trimmed.startsWith("{") || trimmed.startsWith("[")) {
            if (dataPartCount > 1) {
                throw error("Multiple curl data blocks are only supported for form-style bodies");
            }
            return GeneratedBodyKind.JSON;
        }
        if (contentType.contains("application/x-www-form-urlencoded") || looksLikeForm(trimmed)) {
            return GeneratedBodyKind.FORM;
        }
        return GeneratedBodyKind.TEXT;
    }

    private boolean looksLikeForm(String body) {
        return body.contains("=") && !body.contains("\n") && !body.trim().startsWith("{");
    }

    private String renderBody(GeneratedBodyKind bodyKind, String body) {
        if (bodyKind == GeneratedBodyKind.NONE || body == null) {
            return null;
        }
        if (bodyKind == GeneratedBodyKind.FORM) {
            return formBody(body);
        }
        return body;
    }

    private String formBody(String body) {
        var lines = new ArrayList<String>();
        for (var pair : body.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            var parts = pair.split("=", 2);
            var key = decode(parts[0]);
            var value = parts.length == 2 ? decode(parts[1]) : "";
            lines.add(key + "=" + value);
        }
        return String.join("\n", lines);
    }

    private HttpMethod parseMethod(String value) throws DoppioException {
        return HttpMethod.parse(value)
            .orElseThrow(() -> error("Unsupported curl HTTP method: " + value));
    }

    private String parseHeader(String value) throws DoppioException {
        var colonIdx = value.indexOf(':');
        if (colonIdx > 0) {
            return value.substring(0, colonIdx).trim() + "=" + value.substring(colonIdx + 1).trim();
        }

        var eqIdx = value.indexOf('=');
        if (eqIdx > 0) {
            return value.substring(0, eqIdx).trim() + "=" + value.substring(eqIdx + 1).trim();
        }

        throw error("Curl header must use `Name: value`: " + value);
    }

    private ParsedUrl parseUrl(String value) throws DoppioException {
        try {
            var uri = new URI(value);
            var scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw error("Curl URL must use http:// or https://");
            }
            if (uri.getRawAuthority() == null || uri.getRawAuthority().isBlank()) {
                throw error("Curl URL must include a host");
            }

            var baseUrl = uri.getScheme() + "://" + uri.getRawAuthority() + (uri.getRawPath() == null ? "" : uri.getRawPath());
            var queryParams = new ArrayList<String>();
            if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
                for (var pair : uri.getRawQuery().split("&")) {
                    if (pair.isBlank()) {
                        continue;
                    }
                    var parts = pair.split("=", 2);
                    var key = decode(parts[0]);
                    var valuePart = parts.length == 2 ? decode(parts[1]) : "";
                    queryParams.add(valuePart.isEmpty() ? key : key + "=" + valuePart);
                }
            }
            return new ParsedUrl(baseUrl, queryParams);
        } catch (URISyntaxException e) {
            throw error("Invalid curl URL: " + value);
        }
    }

    private String requireValue(List<String> tokens, int index, String option) throws DoppioException {
        if (index >= tokens.size()) {
            throw error("Curl option requires a value: " + option);
        }
        return tokens.get(index);
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private List<String> tokenize(String command) throws DoppioException {
        if (command == null || command.isBlank()) {
            throw error("Curl command cannot be blank");
        }

        var tokens = new ArrayList<String>();
        var current = new StringBuilder();
        var quote = '\0';
        var escaping = false;

        for (var i = 0; i < command.length(); i++) {
            var ch = command.charAt(i);
            if (escaping) {
                if (ch != '\n') {
                    current.append(ch);
                }
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (quote != '\0') {
                if (ch == quote) {
                    quote = '\0';
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }

        if (escaping) {
            current.append('\\');
        }
        if (quote != '\0') {
            throw error("Unterminated quote in curl command");
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private DoppioException error(String message) {
        return new DoppioException(ErrorKind.FILE, message);
    }

    private record ParsedUrl(String baseUrl, List<String> queryParams) {
    }
}
