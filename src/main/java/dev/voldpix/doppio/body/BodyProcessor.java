package dev.voldpix.doppio.body;

import dev.voldpix.doppio.json.JsonParser;
import dev.voldpix.doppio.model.BodyBlock;
import dev.voldpix.doppio.model.BodyKind;
import dev.voldpix.doppio.model.ProcessedBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class BodyProcessor {
    public ProcessedBody process(BodyBlock body) throws BodyException {
        if (body == null || !body.hasContent()) {
            return new ProcessedBody(null, null);
        }

        return switch (body.kind()) {
            case JSON -> processJson(body.content());
            case TEXT -> new ProcessedBody(body.content(), "text/plain; charset=utf-8");
            case CSV -> new ProcessedBody(body.content(), "text/csv; charset=utf-8");
            case FORM -> processForm(body.content());
        };
    }

    private ProcessedBody processJson(String content) throws BodyException {
        new JsonParser(content).parse();
        return new ProcessedBody(content, "application/json");
    }

    private ProcessedBody processForm(String content) throws BodyException {
        var encoded = new StringBuilder();
        var lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        for (var rawLine : lines) {
            var line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            var eqIdx = line.indexOf('=');
            if (eqIdx <= 0) {
                throw new BodyException("Invalid form body line: expected key=value");
            }

            if (!encoded.isEmpty()) {
                encoded.append('&');
            }
            encoded.append(encode(line.substring(0, eqIdx).trim()));
            encoded.append('=');
            encoded.append(encode(line.substring(eqIdx + 1).trim()));
        }

        return new ProcessedBody(encoded.toString(), "application/x-www-form-urlencoded");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
