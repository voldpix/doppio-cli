package dev.voldpix.doppio.console;

import dev.voldpix.doppio.dsl.DslParseException;
import dev.voldpix.doppio.list.RequestListEntry;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.DoppioResponse;
import dev.voldpix.doppio.model.Header;
import dev.voldpix.doppio.model.PreparedRequest;
import dev.voldpix.doppio.model.PreviewReport;
import dev.voldpix.doppio.model.RunReport;
import dev.voldpix.doppio.request.RequestFileInspection;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class JsonFormatter {
    public String formatRun(RunReport report, Path savedReport) {
        var json = new StringBuilder();
        json.append('{');
        field(json, "kind", "run");
        comma(json);
        field(json, "success", report.isSuccess());
        comma(json);
        field(json, "file", absolute(report.requestFile()));
        comma(json);
        request(json, report.request());
        comma(json);
        response(json, report.response());
        if (savedReport != null) {
            comma(json);
            field(json, "savedReport", absolute(savedReport));
        }
        json.append('}');
        return json.toString();
    }

    public String formatPreview(PreviewReport report) {
        var json = new StringBuilder();
        json.append('{');
        field(json, "kind", "preview");
        comma(json);
        field(json, "success", true);
        comma(json);
        field(json, "file", absolute(report.requestFile()));
        comma(json);
        request(json, report.request());
        comma(json);
        json.append("\"body\":");
        body(json, report);
        json.append('}');
        return json.toString();
    }

    public String formatShow(RequestFileInspection inspection) {
        var request = inspection.inspection().request();
        var metadata = inspection.inspection().metadata();
        var json = new StringBuilder();
        json.append('{');
        field(json, "kind", "show");
        comma(json);
        field(json, "success", true);
        comma(json);
        field(json, "file", absolute(inspection.requestFile()));
        comma(json);
        field(json, "relativePath", inspection.relativePath().toString());
        comma(json);
        field(json, "name", request.name());
        comma(json);
        field(json, "method", request.method().name());
        comma(json);
        field(json, "url", request.url());
        comma(json);
        headers(json, request.headers());
        comma(json);
        queryParams(json, request.queryParams());
        comma(json);
        json.append("\"body\":");
        if (request.body() == null) {
            json.append("null");
        } else {
            json.append('{');
            field(json, "kind", request.body().kind().name());
            comma(json);
            field(json, "content", request.body().content());
            json.append('}');
        }
        comma(json);
        json.append("\"variables\":");
        stringMap(json, metadata.variables());
        json.append('}');
        return json.toString();
    }

    public String formatList(Path projectDirectory, List<RequestListEntry> entries) {
        var json = new StringBuilder();
        json.append('{');
        field(json, "kind", "list");
        comma(json);
        field(json, "success", true);
        comma(json);
        field(json, "project", absolute(projectDirectory));
        comma(json);
        json.append("\"requests\":[");
        for (var i = 0; i < entries.size(); i++) {
            if (i > 0) {
                comma(json);
            }
            var entry = entries.get(i);
            json.append('{');
            field(json, "name", entry.displayName());
            comma(json);
            field(json, "path", entry.relativePath().toString());
            if (entry.hasError()) {
                comma(json);
                field(json, "error", entry.error());
            }
            json.append('}');
        }
        json.append(']');
        json.append('}');
        return json.toString();
    }

    public String formatError(DoppioException exception) {
        var json = new StringBuilder();
        json.append('{');
        field(json, "success", false);
        comma(json);
        field(json, "errorKind", exception.kind().name());
        comma(json);
        field(json, "message", exception.getMessage());
        if (exception instanceof DslParseException parseException) {
            comma(json);
            json.append("\"parseErrors\":[");
            for (var i = 0; i < parseException.errors().size(); i++) {
                if (i > 0) {
                    comma(json);
                }
                var error = parseException.errors().get(i);
                json.append('{');
                field(json, "line", error.line());
                comma(json);
                field(json, "hint", error.hint());
                json.append('}');
            }
            json.append(']');
        }
        json.append('}');
        return json.toString();
    }

    private void request(StringBuilder json, PreparedRequest request) {
        json.append("\"request\":{");
        field(json, "name", request.name());
        comma(json);
        field(json, "method", request.method().name());
        comma(json);
        field(json, "url", request.uri().toString());
        comma(json);
        headers(json, request.headers());
        comma(json);
        query(json, request.uri().getRawQuery());
        comma(json);
        json.append("\"body\":");
        if (request.hasBody()) {
            json.append('{');
            field(json, "content", request.body());
            json.append('}');
        } else {
            json.append("null");
        }
        json.append('}');
    }

    private void body(StringBuilder json, PreviewReport report) {
        if (!report.hasBody()) {
            json.append("null");
            return;
        }

        json.append('{');
        field(json, "kind", report.bodyKind() == null ? null : report.bodyKind().name());
        comma(json);
        field(json, "contentType", report.body().contentType());
        comma(json);
        field(json, "content", report.body().content());
        json.append('}');
    }

    private void response(StringBuilder json, DoppioResponse response) {
        json.append("\"response\":{");
        field(json, "status", response.statusCode());
        comma(json);
        field(json, "success", response.isSuccess());
        comma(json);
        field(json, "durationMs", response.duration().toMillis());
        comma(json);
        responseHeaders(json, response.headers());
        comma(json);
        field(json, "body", response.body());
        json.append('}');
    }

    private void headers(StringBuilder json, List<Header> headers) {
        json.append("\"headers\":[");
        for (var i = 0; i < headers.size(); i++) {
            if (i > 0) {
                comma(json);
            }
            var header = headers.get(i);
            json.append('{');
            field(json, "key", header.key());
            comma(json);
            field(json, "value", header.value());
            json.append('}');
        }
        json.append(']');
    }

    private void query(StringBuilder json, String rawQuery) {
        json.append("\"query\":[");
        if (rawQuery != null && !rawQuery.isBlank()) {
            var pairs = Arrays.stream(rawQuery.split("&")).toList();
            for (var i = 0; i < pairs.size(); i++) {
                if (i > 0) {
                    comma(json);
                }
                var parts = pairs.get(i).split("=", 2);
                json.append('{');
                field(json, "key", decode(parts[0]));
                comma(json);
                field(json, "value", parts.length == 2 ? decode(parts[1]) : "");
                json.append('}');
            }
        }
        json.append(']');
    }

    private void queryParams(StringBuilder json, List<dev.voldpix.doppio.model.QueryParam> queryParams) {
        json.append("\"query\":[");
        for (var i = 0; i < queryParams.size(); i++) {
            if (i > 0) {
                comma(json);
            }
            var queryParam = queryParams.get(i);
            json.append('{');
            field(json, "key", queryParam.name());
            comma(json);
            field(json, "value", queryParam.value());
            json.append('}');
        }
        json.append(']');
    }

    private void responseHeaders(StringBuilder json, Map<String, List<String>> headers) {
        json.append("\"headers\":{");
        var entries = headers.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .toList();
        for (var i = 0; i < entries.size(); i++) {
            if (i > 0) {
                comma(json);
            }
            var entry = entries.get(i);
            json.append(quote(entry.getKey())).append(':');
            stringArray(json, entry.getValue());
        }
        json.append('}');
    }

    private void stringMap(StringBuilder json, Map<String, String> values) {
        json.append('{');
        var entries = values.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .toList();
        for (var i = 0; i < entries.size(); i++) {
            if (i > 0) {
                comma(json);
            }
            var entry = entries.get(i);
            json.append(quote(entry.getKey())).append(':').append(quote(entry.getValue()));
        }
        json.append('}');
    }

    private void stringArray(StringBuilder json, List<String> values) {
        json.append('[');
        for (var i = 0; i < values.size(); i++) {
            if (i > 0) {
                comma(json);
            }
            json.append(quote(values.get(i)));
        }
        json.append(']');
    }

    private void field(StringBuilder json, String key, String value) {
        json.append(quote(key)).append(':').append(value == null ? "null" : quote(value));
    }

    private void field(StringBuilder json, String key, boolean value) {
        json.append(quote(key)).append(':').append(value);
    }

    private void field(StringBuilder json, String key, int value) {
        json.append(quote(key)).append(':').append(value);
    }

    private void field(StringBuilder json, String key, long value) {
        json.append(quote(key)).append(':').append(value);
    }

    private String quote(String value) {
        if (value == null) {
            return "null";
        }

        var result = new StringBuilder("\"");
        for (var i = 0; i < value.length(); i++) {
            var ch = value.charAt(i);
            switch (ch) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        result.append(String.format("\\u%04x", (int) ch));
                    } else {
                        result.append(ch);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    private String absolute(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize().toString();
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private void comma(StringBuilder json) {
        json.append(',');
    }
}
