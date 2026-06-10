package dev.voldpix.doppio.request;

import java.util.Locale;
import java.util.Optional;

public enum GeneratedBodyKind {
    NONE,
    JSON,
    TEXT,
    CSV,
    FORM;

    public static Optional<GeneratedBodyKind> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(GeneratedBodyKind.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
