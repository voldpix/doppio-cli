package dev.voldpix.doppio.model;

import java.util.Locale;
import java.util.Optional;

public enum BodyKind {
    JSON,
    TEXT,
    CSV,
    FORM;

    public static Optional<BodyKind> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(JSON);
        }

        try {
            return Optional.of(BodyKind.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
