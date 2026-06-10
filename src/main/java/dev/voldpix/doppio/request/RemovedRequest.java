package dev.voldpix.doppio.request;

import java.nio.file.Path;

public record RemovedRequest(Path relativePath, Path trashFile) {
}
