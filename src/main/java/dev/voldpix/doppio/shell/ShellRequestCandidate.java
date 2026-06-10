package dev.voldpix.doppio.shell;

import java.nio.file.Path;

public record ShellRequestCandidate(Path relativePath, String displayName) {
}
