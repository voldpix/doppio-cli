package dev.voldpix.doppio.pipeline;

import java.nio.file.Path;

public record RequestResolution(Path requestFile, Path seedFile) {
}
