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

        for (var i = 0; i < line.length(); i++) {
            var ch = line.charAt(i);
            if (ch == '\\') {
                if (i + 1 < line.length() && shouldEscape(line.charAt(i + 1), quote)) {
                    current.append(line.charAt(i + 1));
                    i++;
                } else {
                    current.append(ch);
                }
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

        if (quote != '\0') {
            throw new DoppioException(ErrorKind.PARSE, "Unterminated quote");
        }
        addToken(tokens, current);
        return List.copyOf(tokens);
    }

    private boolean shouldEscape(char ch, char quote) {
        if (ch == '\\') {
            return true;
        }
        if (quote != '\0') {
            return ch == quote;
        }
        return Character.isWhitespace(ch) || ch == '\'' || ch == '"';
    }

    private void addToken(ArrayList<String> tokens, StringBuilder current) {
        if (!current.isEmpty()) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }
}
