package dev.voldpix.doppio.check;

import java.util.List;

public record CheckSummary(List<CheckResult> results) {
    public CheckSummary {
        results = List.copyOf(results);
    }

    public long validCount() {
        return results.stream().filter(CheckResult::valid).count();
    }

    public long failedCount() {
        return results.stream().filter(CheckResult::failed).count();
    }

    public boolean hasFailures() {
        return failedCount() > 0;
    }
}
