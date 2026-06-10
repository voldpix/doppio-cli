package dev.voldpix.doppio.expect;

public record Expectation(
    ExpectationKind kind,
    String target,
    String expected,
    String source
) {
    public String label() {
        return switch (kind) {
            case STATUS -> "status=" + expected;
            case HEADER_CONTAINS -> "header " + target + " contains " + expected;
            case BODY_CONTAINS -> "body contains " + expected;
        };
    }
}
