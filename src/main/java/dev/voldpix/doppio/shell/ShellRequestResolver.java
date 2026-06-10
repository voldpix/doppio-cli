package dev.voldpix.doppio.shell;

import dev.voldpix.doppio.list.RequestListEntry;
import dev.voldpix.doppio.list.RequestListService;
import dev.voldpix.doppio.model.DoppioException;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ShellRequestResolver {
    private final RequestListService listService;

    public ShellRequestResolver() {
        this(new RequestListService());
    }

    public ShellRequestResolver(RequestListService listService) {
        this.listService = listService;
    }

    public List<ShellRequestCandidate> all(Path projectRoot) throws DoppioException {
        return listService.list(projectRoot).stream()
            .map(this::candidate)
            .toList();
    }

    public ShellRequestLookup resolve(String target, Path projectRoot) throws DoppioException {
        var candidates = all(projectRoot);
        if (target == null || target.isBlank()) {
            return new ShellRequestLookup(candidates);
        }

        var normalized = normalize(target);
        var exact = candidates.stream()
            .filter(candidate -> exactPath(candidate, normalized))
            .toList();
        if (!exact.isEmpty()) {
            return new ShellRequestLookup(exact);
        }

        var fileMatches = candidates.stream()
            .filter(candidate -> fileName(candidate, normalized))
            .toList();
        if (!fileMatches.isEmpty()) {
            return new ShellRequestLookup(fileMatches);
        }

        var stemMatches = candidates.stream()
            .filter(candidate -> stem(candidate.relativePath().getFileName().toString()).equals(normalized))
            .toList();
        if (!stemMatches.isEmpty()) {
            return new ShellRequestLookup(stemMatches);
        }

        var insensitive = normalized.toLowerCase(Locale.ROOT);
        return new ShellRequestLookup(candidates.stream()
            .filter(candidate -> stem(candidate.relativePath().getFileName().toString()).toLowerCase(Locale.ROOT).equals(insensitive))
            .sorted(Comparator.comparing(candidate -> candidate.relativePath().toString()))
            .toList());
    }

    private ShellRequestCandidate candidate(RequestListEntry entry) {
        return new ShellRequestCandidate(entry.relativePath(), entry.displayName());
    }

    private boolean exactPath(ShellRequestCandidate candidate, String target) {
        var relative = normalize(candidate.relativePath().toString());
        return relative.equals(target) || stripDopo(relative).equals(target);
    }

    private boolean fileName(ShellRequestCandidate candidate, String target) {
        var fileName = candidate.relativePath().getFileName().toString();
        return fileName.equals(target) || stripDopo(fileName).equals(target);
    }

    private String normalize(String value) {
        return value.replace('\\', '/')
            .replaceFirst("^\\.doppio/recipes/", "")
            .replaceFirst("^recipes/", "")
            .trim();
    }

    private String stripDopo(String value) {
        return value.endsWith(".dopo") ? value.substring(0, value.length() - ".dopo".length()) : value;
    }

    private String stem(String fileName) {
        var dot = fileName.lastIndexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }
}
