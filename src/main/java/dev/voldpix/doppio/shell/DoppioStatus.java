package dev.voldpix.doppio.shell;

import java.util.List;

public record DoppioStatus(List<RecentProject> recentProjects) {
    public DoppioStatus {
        recentProjects = List.copyOf(recentProjects == null ? List.of() : recentProjects);
    }

    public static DoppioStatus empty() {
        return new DoppioStatus(List.of());
    }
}
