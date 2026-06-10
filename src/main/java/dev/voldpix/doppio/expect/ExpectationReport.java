package dev.voldpix.doppio.expect;

import java.util.List;

public record ExpectationReport(List<ExpectationResult> results) {
    public ExpectationReport {
        results = List.copyOf(results);
    }

    public static ExpectationReport empty() {
        return new ExpectationReport(List.of());
    }

    public boolean isEmpty() {
        return results.isEmpty();
    }

    public boolean isSuccess() {
        return results.stream().allMatch(ExpectationResult::passed);
    }

    public long passedCount() {
        return results.stream().filter(ExpectationResult::passed).count();
    }

    public long failedCount() {
        return results.stream().filter(result -> !result.passed()).count();
    }
}
