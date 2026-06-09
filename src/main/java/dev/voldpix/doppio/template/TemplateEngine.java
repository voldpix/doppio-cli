package dev.voldpix.doppio.template;

import java.util.Map;
import java.util.regex.Pattern;

public class TemplateEngine {
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*}}");

    public String hydrate(String content, Map<String, String> seedValues, Map<String, String> environment)
        throws TemplateException {
        var matcher = VARIABLE.matcher(content);
        var result = new StringBuilder();

        while (matcher.find()) {
            var key = matcher.group(1);
            var value = resolve(key, seedValues, environment);
            if (value == null) {
                throw new TemplateException("Missing variable: " + key);
            }
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(value));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private String resolve(String key, Map<String, String> seedValues, Map<String, String> environment) {
        if (seedValues.containsKey(key)) {
            return seedValues.get(key);
        }
        return environment.get(key);
    }
}
