package dev.voldpix.doppio.check;

import java.nio.file.Path;

public record CheckResult(
    Path file,
    String displayPath,
    CheckStatus status,
    String message
) {
    public boolean valid() {
        return status == CheckStatus.VALID;
    }

    public boolean failed() {
        return status == CheckStatus.FAILED;
    }
}
