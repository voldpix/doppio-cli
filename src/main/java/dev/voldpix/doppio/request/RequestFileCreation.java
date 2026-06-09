package dev.voldpix.doppio.request;

import java.nio.file.Path;

public record RequestFileCreation(Path requestFile, Path relativePath) {
}
