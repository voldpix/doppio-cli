package dev.voldpix.doppio.list;

import java.nio.file.Path;

public record RequestListEntry(Path relativePath, String displayName, String error) {
    public boolean hasError() {
        return error != null && !error.isBlank();
    }
}
