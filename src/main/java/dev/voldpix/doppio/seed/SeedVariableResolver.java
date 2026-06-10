package dev.voldpix.doppio.seed;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class SeedVariableResolver {
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*}}");

    public Map<String, String> resolve(
        Path seedFile,
        Map<String, String> baseValues,
        Map<String, String> rawValues
    ) throws SeedParseException {
        var resolved = new LinkedHashMap<String, String>();
        resolved.putAll(baseValues);

        var resolvedLocal = new LinkedHashMap<String, String>();
        for (var key : rawValues.keySet()) {
            resolved.put(key, resolveKey(seedFile, key, baseValues, rawValues, resolvedLocal, new ArrayDeque<>()));
        }

        return resolved;
    }

    private String resolveKey(
        Path seedFile,
        String key,
        Map<String, String> baseValues,
        Map<String, String> rawValues,
        Map<String, String> resolvedLocal,
        ArrayDeque<String> stack
    ) throws SeedParseException {
        if (resolvedLocal.containsKey(key)) {
            return resolvedLocal.get(key);
        }
        if (stack.contains(key)) {
            stack.addLast(key);
            throw new SeedParseException("Cyclic seed variable reference in " + seedFile + ": " + String.join(" -> ", stack));
        }

        stack.addLast(key);
        var value = rawValues.get(key);
        var matcher = VARIABLE.matcher(value);
        var result = new StringBuilder();

        while (matcher.find()) {
            var reference = matcher.group(1);
            var replacement = resolveReference(seedFile, key, reference, baseValues, rawValues, resolvedLocal, stack);
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        stack.removeLast();

        var hydrated = result.toString();
        resolvedLocal.put(key, hydrated);
        return hydrated;
    }

    private String resolveReference(
        Path seedFile,
        String key,
        String reference,
        Map<String, String> baseValues,
        Map<String, String> rawValues,
        Map<String, String> resolvedLocal,
        ArrayDeque<String> stack
    ) throws SeedParseException {
        if (rawValues.containsKey(reference)) {
            return resolveKey(seedFile, reference, baseValues, rawValues, resolvedLocal, stack);
        }
        var baseValue = baseValues.get(reference);
        if (baseValue != null) {
            return baseValue;
        }
        throw new SeedParseException("Missing seed variable in " + seedFile + ": " + key + " references " + reference);
    }
}
