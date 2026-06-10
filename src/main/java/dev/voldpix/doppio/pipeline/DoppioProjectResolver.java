package dev.voldpix.doppio.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;

public class DoppioProjectResolver {
    public Path findDoppioDirectory(Path workingDirectory) {
        var current = workingDirectory.toAbsolutePath().normalize();
        while (current != null) {
            if (isDoppioDirectory(current)) {
                return current;
            }

            var childDoppio = current.resolve(".doppio");
            if (isDoppioDirectory(childDoppio)) {
                return childDoppio;
            }

            current = current.getParent();
        }
        return null;
    }

    private boolean isDoppioDirectory(Path path) {
        return path != null
            && ".doppio".equals(path.getFileName() == null ? "" : path.getFileName().toString())
            && Files.isDirectory(path)
            && (Files.exists(path.resolve("default.seed"))
                || Files.exists(path.resolve("local.seed"))
                || Files.isDirectory(path.resolve("requests")));
    }
}
