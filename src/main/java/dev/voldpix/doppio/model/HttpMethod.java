package dev.voldpix.doppio.model;

import java.util.Arrays;
import java.util.Optional;

public enum HttpMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE;

    public static Optional<HttpMethod> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }

        return Arrays.stream(values())
            .filter(method -> method.name().equalsIgnoreCase(value.trim()))
            .findFirst();
    }
}
