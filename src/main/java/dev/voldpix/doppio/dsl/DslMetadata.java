package dev.voldpix.doppio.dsl;

import dev.voldpix.doppio.expect.Expectation;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public record DslMetadata(String name, Map<String, String> variables, List<Expectation> expectations) {
    public DslMetadata {
        variables = Map.copyOf(new LinkedHashMap<>(variables));
        expectations = List.copyOf(expectations);
    }
}
