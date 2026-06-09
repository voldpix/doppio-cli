package dev.voldpix.doppio.request;

import dev.voldpix.doppio.dsl.DslInspection;

import java.nio.file.Path;

public record RequestFileInspection(Path requestFile, Path relativePath, DslInspection inspection) {
}
