package dev.voldpix.doppio.format;

import dev.voldpix.doppio.body.BodyException;
import dev.voldpix.doppio.dsl.DslProcessor;
import dev.voldpix.doppio.model.BodyKind;
import dev.voldpix.doppio.model.DoppioException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class DopoSourceFormatter {
    private static final String BODY_CLOSE = "|>";
    private static final Pattern BODY_OPEN = Pattern.compile("^<(?:(json|text|csv|form))?\\|$", Pattern.CASE_INSENSITIVE);

    private final DslProcessor dslProcessor;
    private final CommentedJsonFormatter jsonFormatter;

    public DopoSourceFormatter() {
        this(new DslProcessor(), new CommentedJsonFormatter());
    }

    public DopoSourceFormatter(DslProcessor dslProcessor, CommentedJsonFormatter jsonFormatter) {
        this.dslProcessor = dslProcessor;
        this.jsonFormatter = jsonFormatter;
    }

    public String format(String source) throws DoppioException {
        dslProcessor.inspect(source);

        var output = new ArrayList<String>();
        var lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (var index = 0; index < lines.length; index++) {
            var rawLine = lines[index];
            var trimmed = rawLine.trim();
            var bodyKind = parseBodyOpen(trimmed);
            if (bodyKind != null) {
                addBlankBeforeBody(output);
                output.add(normalizeBodyOpen(trimmed));

                var bodyLines = new ArrayList<String>();
                index++;
                while (index < lines.length && !BODY_CLOSE.equals(lines[index].trim())) {
                    bodyLines.add(lines[index]);
                    index++;
                }
                output.addAll(formatBody(bodyKind, bodyLines));
                output.add(BODY_CLOSE);
                continue;
            }

            if (trimmed.isEmpty()) {
                continue;
            }
            output.add(normalizeOutsideLine(rawLine));
        }

        return String.join("\n", output) + "\n";
    }

    private void addBlankBeforeBody(List<String> output) {
        if (!output.isEmpty() && !output.getLast().isBlank()) {
            output.add("");
        }
    }

    private BodyKind parseBodyOpen(String line) {
        var matcher = BODY_OPEN.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        return BodyKind.parse(matcher.group(1)).orElse(BodyKind.JSON);
    }

    private String normalizeBodyOpen(String line) {
        var matcher = BODY_OPEN.matcher(line);
        if (!matcher.matches()) {
            return line.trim();
        }
        var type = matcher.group(1);
        return type == null ? "<|" : "<" + type.toLowerCase(Locale.ROOT) + "|";
    }

    private List<String> formatBody(BodyKind kind, List<String> bodyLines) throws DoppioException {
        var body = trimOuterBlankLines(bodyLines);
        if (body.isBlank()) {
            throw new BodyException("body block is empty");
        }

        return switch (kind) {
            case JSON -> jsonFormatter.format(body).lines().toList();
            case TEXT, CSV -> trimTrailingWhitespace(body).lines().toList();
            case FORM -> formatFormBody(body);
        };
    }

    private String normalizeOutsideLine(String line) {
        var normalized = line.trim().replaceAll("\\s+", " ");
        if (normalized.equals("header") || normalized.startsWith("header ")) {
            return "-h " + normalized.replaceFirst("^header(\\s+|$)", "").trim();
        }
        if (normalized.equals("query") || normalized.startsWith("query ")) {
            return "-q " + normalized.replaceFirst("^query(\\s+|$)", "").trim();
        }
        return normalized;
    }

    private List<String> formatFormBody(String body) throws BodyException {
        var result = new ArrayList<String>();
        for (var rawLine : trimTrailingWhitespace(body).split("\n", -1)) {
            var line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("#")) {
                result.add(line);
                continue;
            }

            var eqIdx = line.indexOf('=');
            if (eqIdx <= 0) {
                throw new BodyException("Invalid form body line: expected key=value");
            }
            var key = line.substring(0, eqIdx).trim();
            var value = line.substring(eqIdx + 1).trim();
            if (key.isBlank()) {
                throw new BodyException("Invalid form body line: key is missing");
            }
            result.add(key + "=" + value);
        }
        return result;
    }

    private String trimOuterBlankLines(List<String> lines) {
        var start = 0;
        var end = lines.size();

        while (start < end && lines.get(start).isBlank()) {
            start++;
        }
        while (end > start && lines.get(end - 1).isBlank()) {
            end--;
        }

        return trimTrailingWhitespace(String.join("\n", lines.subList(start, end)));
    }

    private String trimTrailingWhitespace(String content) {
        var result = new StringBuilder();
        var lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (var i = 0; i < lines.length; i++) {
            if (i > 0) {
                result.append('\n');
            }
            result.append(lines[i].stripTrailing());
        }
        return result.toString().strip();
    }
}
