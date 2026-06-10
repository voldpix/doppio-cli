package dev.voldpix.doppio.expect;

import dev.voldpix.doppio.model.DoppioResponse;

import java.util.List;

public class ExpectationEvaluator {
    public ExpectationReport evaluate(List<Expectation> expectations, DoppioResponse response) {
        if (expectations == null || expectations.isEmpty()) {
            return ExpectationReport.empty();
        }

        return new ExpectationReport(expectations.stream()
            .map(expectation -> evaluate(expectation, response))
            .toList());
    }

    private ExpectationResult evaluate(Expectation expectation, DoppioResponse response) {
        return switch (expectation.kind()) {
            case STATUS -> status(expectation, response);
            case HEADER_CONTAINS -> headerContains(expectation, response);
            case BODY_CONTAINS -> bodyContains(expectation, response);
        };
    }

    private ExpectationResult status(Expectation expectation, DoppioResponse response) {
        var expected = Integer.parseInt(expectation.expected());
        var passed = response.statusCode() == expected;
        return new ExpectationResult(
            expectation,
            passed,
            passed ? "status matched" : "expected status " + expected + " but got " + response.statusCode()
        );
    }

    private ExpectationResult headerContains(Expectation expectation, DoppioResponse response) {
        var values = response.headers().entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(expectation.target()))
            .flatMap(entry -> entry.getValue().stream())
            .toList();
        var joined = String.join(", ", values);
        var passed = joined.contains(expectation.expected());
        var headerName = expectation.target();
        var message = passed
            ? "header matched"
            : values.isEmpty()
                ? "response header not found: " + headerName
                : "response header " + headerName + " did not contain " + expectation.expected();
        return new ExpectationResult(expectation, passed, message);
    }

    private ExpectationResult bodyContains(Expectation expectation, DoppioResponse response) {
        var passed = response.body().contains(expectation.expected());
        return new ExpectationResult(
            expectation,
            passed,
            passed ? "body matched" : "response body did not contain " + expectation.expected()
        );
    }
}
