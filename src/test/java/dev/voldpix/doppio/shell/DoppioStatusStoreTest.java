package dev.voldpix.doppio.shell;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DoppioStatusStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsRecentProjects() throws Exception {
        var store = new DoppioStatusStore(tempDir.resolve("config"));
        var project = tempDir.resolve("project");

        store.write(new DoppioStatus(List.of(new RecentProject(project, Instant.parse("2026-06-10T13:00:00Z")))));

        assertThat(store.read().recentProjects())
            .extracting(RecentProject::path)
            .containsExactly(project.toAbsolutePath().normalize());
    }

    @Test
    void filtersInvalidRecentProjects() throws Exception {
        var valid = tempDir.resolve("valid");
        Files.createDirectories(valid.resolve(".doppio/requests"));
        var missing = tempDir.resolve("missing");
        var store = new DoppioStatusStore(tempDir.resolve("config"));
        store.write(new DoppioStatus(List.of(
            new RecentProject(missing, Instant.parse("2026-06-10T13:00:00Z")),
            new RecentProject(valid, Instant.parse("2026-06-10T14:00:00Z"))
        )));

        assertThat(store.validRecentProjects())
            .extracting(RecentProject::path)
            .containsExactly(valid.toAbsolutePath().normalize());
    }
}
