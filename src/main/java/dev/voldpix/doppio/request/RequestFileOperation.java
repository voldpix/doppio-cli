package dev.voldpix.doppio.request;

import java.nio.file.Path;

public record RequestFileOperation(Path sourceRelativePath, Path destinationRelativePath, Path destinationFile) {
}
