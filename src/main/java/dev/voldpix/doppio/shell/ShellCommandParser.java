package dev.voldpix.doppio.shell;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;

import java.util.ArrayList;
import java.util.List;

public class ShellCommandParser {
    public List<String> parse(String line) throws DoppioException {
        if (line == null || line.isBlank()) {
            return List.of();
        }

        var tokens = new ArrayList<String>();
        var current = new StringBuilder();
        var quote = '\0';
        var escaping = false;

        for (var i = 0; i < line.length(); i++) {
            var ch = line.charAt(i);
            if (escaping) {
                current.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (quote != '\0') {
                if (ch == quote) {
                    quote = '\0';
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                addToken(tokens, current);
                continue;
            }
            current.append(ch);
        }

        if (escaping) {
            current.append('\\');
        }
        if (quote != '\0') {
            throw new DoppioException(ErrorKind.PARSE, "Unterminated quote");
        }
        addToken(tokens, current);
        return List.copyOf(tokens);
    }

    private void addToken(ArrayList<String> tokens, StringBuilder current) {
        if (!current.isEmpty()) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }
}
