package dev.voldpix.doppio.shell;

import dev.voldpix.doppio.pipeline.DoppioProjectResolver;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

public class DoppioStatusStore {
    private static final int MAX_RECENT_PROJECTS = 20;
    private static final Pattern PROJECT_PATTERN = Pattern.compile(
        "\\{\\s*\"path\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"\\s*,\\s*\"lastUsedAt\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"\\s*}"
    );

    private final Path configDirectory;
    private final Path statusFile;
    private final Path historyFile;
    private final DoppioProjectResolver projectResolver;

    public DoppioStatusStore(Path configDirectory) {
        this(configDirectory, new DoppioProjectResolver());
    }

    public DoppioStatusStore(Path configDirectory, DoppioProjectResolver projectResolver) {
        this.configDirectory = configDirectory.toAbsolutePath().normalize();
        this.statusFile = this.configDirectory.resolve("status.json");
        this.historyFile = this.configDirectory.resolve("history");
        this.projectResolver = projectResolver;
    }

    public static DoppioStatusStore userDefault() {
        return new DoppioStatusStore(Path.of(System.getProperty("user.home"), ".config", "doppio"));
    }

    public Path configDirectory() {
        return configDirectory;
    }

    public Path historyFile() {
        return historyFile;
    }

    public DoppioStatus read() {
        if (!Files.isRegularFile(statusFile)) {
            return DoppioStatus.empty();
        }

        try {
            var content = Files.readString(statusFile);
            var matcher = PROJECT_PATTERN.matcher(content);
            var projects = new ArrayList<RecentProject>();
            while (matcher.find()) {
                projects.add(new RecentProject(
                    Path.of(unescape(matcher.group(1))).toAbsolutePath().normalize(),
                    parseInstant(unescape(matcher.group(2)))
                ));
            }
            return new DoppioStatus(projects);
        } catch (IOException | RuntimeException e) {
            return DoppioStatus.empty();
        }
    }

    public List<RecentProject> validRecentProjects() {
        return read().recentProjects().stream()
            .map(this::validProject)
            .flatMap(java.util.Optional::stream)
            .sorted(Comparator.comparing(RecentProject::lastUsedAt).reversed())
            .toList();
    }

    public void recordProject(Path projectRoot) {
        var normalized = projectRoot.toAbsolutePath().normalize();
        var deduped = new LinkedHashMap<Path, RecentProject>();
        deduped.put(normalized, new RecentProject(normalized, Instant.now()));
        read().recentProjects().stream()
            .filter(project -> !project.path().equals(normalized))
            .forEach(project -> deduped.put(project.path(), project));

        write(new DoppioStatus(deduped.values().stream()
            .limit(MAX_RECENT_PROJECTS)
            .toList()));
    }

    public void write(DoppioStatus status) {
        try {
            Files.createDirectories(configDirectory);
            var tmp = configDirectory.resolve("status.json.tmp");
            Files.writeString(tmp, toJson(status));
            try {
                Files.move(tmp, statusFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, statusFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // Shell history/status should never block request work.
        }
    }

    private java.util.Optional<RecentProject> validProject(RecentProject project) {
        var doppioDir = projectResolver.findDoppioDirectory(project.path());
        if (doppioDir == null) {
            return java.util.Optional.empty();
        }
        var root = projectRoot(doppioDir);
        return java.util.Optional.of(new RecentProject(root, project.lastUsedAt()));
    }

    private Path projectRoot(Path doppioDir) {
        return ".doppio".equals(doppioDir.getFileName().toString()) && doppioDir.getParent() != null
            ? doppioDir.getParent().toAbsolutePath().normalize()
            : doppioDir.toAbsolutePath().normalize();
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return Instant.EPOCH;
        }
    }

    private String toJson(DoppioStatus status) {
        var builder = new StringBuilder();
        builder.append("{\n  \"version\": 1,\n  \"recentProjects\": [");
        for (var i = 0; i < status.recentProjects().size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            var project = status.recentProjects().get(i);
            builder.append("\n    { \"path\": \"")
                .append(escape(project.path().toString()))
                .append("\", \"lastUsedAt\": \"")
                .append(escape(project.lastUsedAt().toString()))
                .append("\" }");
        }
        if (!status.recentProjects().isEmpty()) {
            builder.append('\n');
        }
        builder.append("  ]\n}\n");
        return builder.toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unescape(String value) {
        var result = new StringBuilder();
        var escaping = false;
        for (var i = 0; i < value.length(); i++) {
            var ch = value.charAt(i);
            if (escaping) {
                result.append(ch);
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else {
                result.append(ch);
            }
        }
        if (escaping) {
            result.append('\\');
        }
        return result.toString();
    }
}
