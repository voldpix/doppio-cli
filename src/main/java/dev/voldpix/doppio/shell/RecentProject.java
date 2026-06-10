package dev.voldpix.doppio.shell;

import java.nio.file.Path;
import java.time.Instant;

public record RecentProject(Path path, Instant lastUsedAt) {
}
