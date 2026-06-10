package dev.voldpix.doppio.format;

import java.nio.file.Path;

public record FormatResult(
    Path file,
    String displayPath,
    FormatStatus status,
    String message
) {
    public boolean changed() {
        return status == FormatStatus.CHANGED;
    }

    public boolean unchanged() {
        return status == FormatStatus.UNCHANGED;
    }

    public boolean failed() {
        return status == FormatStatus.FAILED;
    }
}
