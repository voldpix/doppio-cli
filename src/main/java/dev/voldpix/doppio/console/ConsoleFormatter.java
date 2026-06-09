package dev.voldpix.doppio.console;

import dev.voldpix.doppio.dsl.DslParseException;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.Header;
import dev.voldpix.doppio.model.PreparedRequest;
import dev.voldpix.doppio.model.RunReport;

import java.io.PrintWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ConsoleFormatter {
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String CYAN = "\u001B[36m";
    private static final String GRAY = "\u001B[90m";

    public void printReport(RunReport report, PrintWriter out) {
        var request = report.request();
        var response = report.response();
        var statusColor = response.isSuccess() ? GREEN : RED;
        var label = response.isSuccess() ? "SUCCESS" : "FAILED";

        out.println();
        out.printf("%s%s%s %s %s%n", BOLD, request.method(), RESET, CYAN + request.uri() + RESET, "");
        if (request.name() != null && !request.name().isBlank()) {
            out.printf("%s%s%s%n", GRAY, request.name(), RESET);
        }
        printRequestDetails(request, out);

        out.println();
        out.printf("%s%s %d%s  %s%dms%s%n",
            statusColor,
            label,
            response.statusCode(),
            RESET,
            GRAY,
            response.duration().toMillis(),
            RESET);

        if (!response.headers().isEmpty()) {
            out.println();
            out.println(BOLD + "Response Headers" + RESET);
            response.headers().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> out.printf("  %s: %s%n", entry.getKey(), String.join(", ", entry.getValue())));
        }

        out.println();
        out.println(BOLD + "Response Body" + RESET);
        if (response.body().isBlank()) {
            out.println(GRAY + "(empty)" + RESET);
        } else {
            out.println(response.body());
        }

        out.flush();
    }

    public void printError(DoppioException exception, PrintWriter err) {
        err.printf("%s%s Error:%s %s%n", RED, title(exception), RESET, exception.getMessage());

        if (exception instanceof DslParseException parseException) {
            for (var parseError : parseException.errors()) {
                err.printf("  Near: \"%s\"%n", parseError.line());
                err.printf("  Hint: %s%n", parseError.hint());
            }
        }

        err.flush();
    }

    private void printRequestDetails(PreparedRequest request, PrintWriter out) {
        out.println();
        out.println(BOLD + "Request" + RESET);
        out.printf("  URL: %s%n", request.uri());

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

    private String title(DoppioException exception) {
        var lower = exception.kind().name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
