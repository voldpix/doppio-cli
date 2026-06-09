package dev.voldpix.doppio.json;

final class JsonParser {
    private final String source;
    private int index;

    JsonParser(String source) {
        this.source = source;
    }

    void parse() throws JsonBodyException {
        skipWhitespace();
        parseValue();
        skipWhitespace();
        if (!isAtEnd()) {
            fail("unexpected content after JSON value");
        }
    }

    private void parseValue() throws JsonBodyException {
        skipWhitespace();
        if (isAtEnd()) {
            fail("empty JSON body");
        }

        var next = peek();
        switch (next) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't' -> consumeLiteral("true");
            case 'f' -> consumeLiteral("false");
            case 'n' -> consumeLiteral("null");
            default -> {
                if (next == '-' || Character.isDigit(next)) {
                    parseNumber();
                } else {
                    fail("expected JSON value");
                }
            }
        }
    }

    private void parseObject() throws JsonBodyException {
        consume('{');
        skipWhitespace();
        if (tryConsume('}')) {
            return;
        }

        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                fail("expected object key string");
            }
            parseString();
            skipWhitespace();
            consume(':');
            parseValue();
            skipWhitespace();
            if (tryConsume('}')) {
                return;
            }
            consume(',');
        }
    }

    private void parseArray() throws JsonBodyException {
        consume('[');
        skipWhitespace();
        if (tryConsume(']')) {
            return;
        }

        while (true) {
            parseValue();
            skipWhitespace();
            if (tryConsume(']')) {
                return;
            }
            consume(',');
        }
    }

    private void parseString() throws JsonBodyException {
        consume('"');
        while (!isAtEnd()) {
            var current = source.charAt(index++);
            if (current == '"') {
                return;
            }
            if (current < 0x20) {
                fail("unescaped control character in string");
            }
            if (current == '\\') {
                parseEscape();
            }
        }
        fail("unterminated string");
    }

    private void parseEscape() throws JsonBodyException {
        if (isAtEnd()) {
            fail("unterminated escape sequence");
        }

        var escaped = source.charAt(index++);
        switch (escaped) {
            case '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {
            }
            case 'u' -> parseUnicodeEscape();
            default -> fail("invalid escape sequence");
        }
    }

    private void parseUnicodeEscape() throws JsonBodyException {
        for (var i = 0; i < 4; i++) {
            if (isAtEnd() || Character.digit(source.charAt(index++), 16) == -1) {
                fail("invalid unicode escape");
            }
        }
    }

    private void parseNumber() throws JsonBodyException {
        if (tryConsume('-') && isAtEnd()) {
            fail("invalid number");
        }

        if (tryConsume('0')) {
            if (!isAtEnd() && Character.isDigit(peek())) {
                fail("leading zero is not allowed");
            }
        } else {
            consumeDigits("expected number");
        }

        if (tryConsume('.')) {
            consumeDigits("expected digits after decimal point");
        }

        if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
            index++;
            if (!isAtEnd() && (peek() == '+' || peek() == '-')) {
                index++;
            }
            consumeDigits("expected exponent digits");
        }
    }

    private void consumeDigits(String message) throws JsonBodyException {
        if (isAtEnd() || !Character.isDigit(peek())) {
            fail(message);
        }
        while (!isAtEnd() && Character.isDigit(peek())) {
            index++;
        }
    }

    private void consumeLiteral(String literal) throws JsonBodyException {
        if (!source.startsWith(literal, index)) {
            fail("expected " + literal);
        }
        index += literal.length();
    }

    private void skipWhitespace() {
        while (!isAtEnd() && Character.isWhitespace(peek())) {
            index++;
        }
    }

    private boolean tryConsume(char expected) {
        if (!isAtEnd() && peek() == expected) {
            index++;
            return true;
        }
        return false;
    }

    private void consume(char expected) throws JsonBodyException {
        if (!tryConsume(expected)) {
            fail("expected '" + expected + "'");
        }
    }

    private char peek() {
        return source.charAt(index);
    }

    private boolean isAtEnd() {
        return index >= source.length();
    }

    private void fail(String message) throws JsonBodyException {
        throw new JsonBodyException(message + " at character " + (index + 1));
    }
}
