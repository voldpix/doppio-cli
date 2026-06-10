package dev.voldpix.doppio.console;

import dev.voldpix.doppio.dsl.DslParseException;
import dev.voldpix.doppio.expect.ExpectationReport;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.Header;
import dev.voldpix.doppio.model.PreparedRequest;
import dev.voldpix.doppio.model.PreviewReport;
import dev.voldpix.doppio.model.RunReport;

import java.io.PrintWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

public class ConsoleFormatter {
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String CYAN = "\u001B[36m";
    private static final String GRAY = "\u001B[90m";
    private final boolean ansi;

    public ConsoleFormatter() {
        this(true);
    }

    public ConsoleFormatter(boolean ansi) {
        this.ansi = ansi;
    }

    public void printReport(RunReport report, PrintWriter out) {
        out.print(formatReport(report));
        out.flush();
    }

    public String formatReport(RunReport report) {
        var output = new java.io.StringWriter();
        var out = new PrintWriter(output);
        var request = report.request();
        var response = report.response();
        var label = report.isSuccess() ? "SUCCESS" : "FAILED";

        out.println();
        out.println(style("Doppio Run", BOLD));
        if (request.name() != null && !request.name().isBlank()) {
            out.printf("Name: %s%n", request.name());
        }
        out.printf("File: %s%n", report.requestFile());
        if (report.environmentName() != null) {
            out.printf("Seed: %s%n", report.environmentName());
        }
        out.printf("Request: %s %s%n", style(request.method().name(), BOLD), style(request.uri().toString(), CYAN));
        out.printf("Result: %s %d  %dms%n",
            style(label, report.isSuccess() ? GREEN : RED),
            response.statusCode(),
            response.duration().toMillis());

        printRequestDetails(request, out);

        if (!response.headers().isEmpty()) {
            out.println();
            out.println(style("Response Headers", BOLD));
            response.headers().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> out.printf("  %s: %s%n", entry.getKey(), String.join(", ", entry.getValue())));
        }

        out.println();
        out.println(style("Response Body", BOLD));
        if (response.body().isBlank()) {
            out.println(style("(empty)", GRAY));
        } else {
            out.println(formatBody(response));
        }

        printExpectationReport(report.expectations(), out);

        out.flush();
        return output.toString();
    }

    public void printSavedReport(java.nio.file.Path savedPath, PrintWriter out) {
        out.printf("%n%s%s%s %s%n", color(GRAY), "Saved report:", color(RESET), savedPath);
        out.flush();
    }

    public void printPreview(PreviewReport report, PrintWriter out) {
        out.println();
        out.println(style("Doppio Preview", BOLD));
        if (report.request().name() != null && !report.request().name().isBlank()) {
            out.printf("Name: %s%n", report.request().name());
        }
        out.printf("File: %s%n", report.requestFile());
        if (report.environmentName() != null) {
            out.printf("Seed: %s%n", report.environmentName());
        }
        out.printf("Request: %s %s%n",
            style(report.request().method().name(), BOLD),
            style(report.request().uri().toString(), CYAN));
        printRequestDetails(report.request(), out);
        if (!report.expectations().isEmpty()) {
            out.println();
            out.println(style("Expectations", BOLD));
            report.expectations().forEach(expectation -> out.println("  " + expectation.label()));
        }
        if (report.hasBody()) {
            out.println();
            out.println(style("Request Body", BOLD));
            out.println(report.body().content());
        }
        out.flush();
    }

    public void printError(DoppioException exception, PrintWriter err) {
        err.printf("%s%s Error:%s %s%n", color(RED), title(exception), color(RESET), exception.getMessage());

        if (exception instanceof DslParseException parseException) {
            for (var parseError : parseException.errors()) {
                err.printf("  Near: \"%s\"%n", parseError.line());
                err.printf("  Hint: %s%n", parseError.hint());
            }
        }

        err.flush();
    }

    private void printExpectationReport(ExpectationReport expectations, PrintWriter out) {
        if (expectations == null || expectations.isEmpty()) {
            return;
        }

        out.println();
        out.println(style("Expectations", BOLD));
        out.printf("  Passed: %d%n", expectations.passedCount());
        out.printf("  Failed: %d%n", expectations.failedCount());
        expectations.results().forEach(result -> out.printf(
            "  %s %s%s%n",
            style(result.passed() ? "PASS" : "FAIL", result.passed() ? GREEN : RED),
            result.expectation().label(),
            result.passed() ? "" : " - " + result.message()
        ));
    }

    private void printRequestDetails(PreparedRequest request, PrintWriter out) {
        out.println();
        out.println(style("Request Details", BOLD));
        out.printf("  URL: %s%n", request.uri());
        if (request.name() != null && !request.name().isBlank()) {
            out.printf("  Name: %s%n", request.name());
        }

        if (!request.headers().isEmpty()) {
            out.println("  Headers:");
            for (Header header : request.headers()) {
                out.printf("    %s: %s%n", header.key(), header.value());
            }
        }

        var query = request.uri().getRawQuery();
        if (query != null && !query.isBlank()) {
            out.println("  Query:");
            Arrays.stream(query.split("&"))
                .map(pair -> pair.split("=", 2))
                .forEach(parts -> out.printf("    %s = %s%n", decode(parts[0]), parts.length == 2 ? decode(parts[1]) : ""));
        }
    }

    private String formatBody(dev.voldpix.doppio.model.DoppioResponse response) {
        if (!isJsonResponse(response)) {
            return response.body();
        }

        return prettyJson(response.body());
    }

    private boolean isJsonResponse(dev.voldpix.doppio.model.DoppioResponse response) {
        return response.headers().entrySet().stream()
            .filter(entry -> "content-type".equalsIgnoreCase(entry.getKey()))
            .flatMap(entry -> entry.getValue().stream())
            .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains("json"));
    }

    private String prettyJson(String body) {
        var trimmed = body.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return body;
        }

        var result = new StringBuilder();
        var indent = 0;
        var inString = false;
        var escaped = false;

        for (var i = 0; i < trimmed.length(); i++) {
            var ch = trimmed.charAt(i);
            if (escaped) {
                result.append(ch);
                escaped = false;
                continue;
            }
            if (ch == '\\' && inString) {
                result.append(ch);
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                result.append(ch);
                continue;
            }
            if (inString) {
                result.append(ch);
                continue;
            }

            switch (ch) {
                case '{', '[' -> {
                    result.append(ch).append('\n');
                    indent++;
                    appendIndent(result, indent);
                }
                case '}', ']' -> {
                    result.append('\n');
                    indent = Math.max(0, indent - 1);
                    appendIndent(result, indent);
                    result.append(ch);
                }
                case ',' -> {
                    result.append(ch).append('\n');
                    appendIndent(result, indent);
                }
                case ':' -> result.append(": ");
                default -> {
                    if (!Character.isWhitespace(ch)) {
                        result.append(ch);
                    }
                }
            }
        }

        return result.toString();
    }

    private void appendIndent(StringBuilder result, int indent) {
        result.append("  ".repeat(Math.max(0, indent)));
    }

    private String title(DoppioException exception) {
        var lower = exception.kind().name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String style(String text, String style) {
        return color(style) + text + color(RESET);
    }

    private String color(String code) {
        return ansi ? code : "";
    }
}
