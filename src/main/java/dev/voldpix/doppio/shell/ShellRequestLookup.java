package dev.voldpix.doppio.shell;

import java.util.List;

public record ShellRequestLookup(List<ShellRequestCandidate> matches) {
    public ShellRequestLookup {
        matches = List.copyOf(matches);
    }

    public boolean found() {
        return !matches.isEmpty();
    }

    public boolean ambiguous() {
        return matches.size() > 1;
    }

    public ShellRequestCandidate only() {
        return matches.getFirst();
    }
}
