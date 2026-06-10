package dev.voldpix.doppio.expect;

public record ExpectationResult(
    Expectation expectation,
    boolean passed,
    String message
) {
}
