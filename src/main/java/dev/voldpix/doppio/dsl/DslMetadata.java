package dev.voldpix.doppio.dsl;

import java.util.LinkedHashMap;
import java.util.Map;

public record DslMetadata(String name, Map<String, String> variables) {
    public DslMetadata {
        variables = Map.copyOf(new LinkedHashMap<>(variables));
    }
}
