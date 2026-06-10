package dev.voldpix.doppio.format;

import java.util.List;

public record FormatSummary(List<FormatResult> results) {
    public FormatSummary {
        results = List.copyOf(results);
    }

    public long changedCount() {
        return results.stream().filter(FormatResult::changed).count();
    }

    public long unchangedCount() {
        return results.stream().filter(FormatResult::unchanged).count();
    }

    public long failedCount() {
        return results.stream().filter(FormatResult::failed).count();
    }

    public boolean hasFailures() {
        return failedCount() > 0;
    }
}
