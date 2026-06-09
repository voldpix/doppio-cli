package dev.voldpix.doppio.seed;

import java.util.LinkedHashMap;
import java.util.Map;

public class SeedParser {
    public Map<String, String> parse(String content) throws SeedParseException {
        var values = new LinkedHashMap<String, String>();

        if (content == null || content.isBlank()) {
            return values;
        }

        var lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (var i = 0; i < lines.length; i++) {
            var lineNumber = i + 1;
            var trimmed = lines[i].trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            var eqIdx = trimmed.indexOf('=');
            if (eqIdx <= 0) {
                throw new SeedParseException("Invalid seed entry on line " + lineNumber + ": expected KEY=value");
            }

            var key = trimmed.substring(0, eqIdx).trim();
            var value = stripMatchingQuotes(trimmed.substring(eqIdx + 1).trim());

            if (key.isEmpty()) {
                throw new SeedParseException("Invalid seed entry on line " + lineNumber + ": key is missing");
            }
            if (key.chars().anyMatch(Character::isWhitespace)) {
                throw new SeedParseException("Invalid seed entry on line " + lineNumber + ": key cannot contain whitespace");
            }
            if (values.containsKey(key)) {
                throw new SeedParseException("Duplicate seed key on line " + lineNumber + ": " + key);
            }

            values.put(key, value);
        }

        return values;
    }

    private String stripMatchingQuotes(String value) {
        if (value.length() < 2) {
            return value;
        }

        var first = value.charAt(0);
        var last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }
}
