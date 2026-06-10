package dev.voldpix.doppio.format;

import dev.voldpix.doppio.body.BodyException;
import dev.voldpix.doppio.json.JsonParser;

import java.util.ArrayList;
import java.util.List;

public class CommentedJsonFormatter {
    public String format(String source) throws BodyException {
        if (source == null || source.isBlank()) {
            throw new BodyException("JSON body is empty");
        }

        var parser = new Parser(new Tokenizer(source).tokenize());
        var formatted = parser.parse();
        new JsonParser(stripFullLineComments(source)).parse();
        return formatted;
    }

    private String stripFullLineComments(String source) {
        var output = new StringBuilder();
        var lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (var line : lines) {
            if (!line.trim().startsWith("#")) {
                output.append(line);
            }
            output.append('\n');
        }
        return output.toString();
    }

    private enum TokenType {
        LEFT_BRACE,
        RIGHT_BRACE,
        LEFT_BRACKET,
        RIGHT_BRACKET,
        COLON,
        COMMA,
        STRING,
        NUMBER,
        LITERAL,
        COMMENT,
        EOF
    }

    private record Token(TokenType type, String text) {
    }

    private interface JsonNode {
        String format(int indent);
    }

    private interface ObjectPart {
    }

    private interface ArrayPart {
    }

    private record ScalarNode(String text) implements JsonNode {
        @Override
        public String format(int indent) {
            return text;
        }
    }

    private record ObjectNode(List<ObjectPart> parts) implements JsonNode {
        @Override
        public String format(int indent) {
            if (parts.isEmpty()) {
                return "{}";
            }

            var output = new StringBuilder("{\n");
            for (var i = 0; i < parts.size(); i++) {
                var part = parts.get(i);
                if (part instanceof CommentPart comment) {
                    output.append(indent(indent + 1)).append(comment.text()).append('\n');
                    continue;
                }

                var entry = (EntryPart) part;
                var line = indent(indent + 1) + entry.key() + ": " + entry.value().format(indent + 1);
                if (hasLaterEntry(parts, i)) {
                    line = appendComma(line);
                }
                output.append(line).append('\n');
            }
            output.append(indent(indent)).append('}');
            return output.toString();
        }

        private boolean hasLaterEntry(List<ObjectPart> parts, int index) {
            return parts.subList(index + 1, parts.size()).stream().anyMatch(EntryPart.class::isInstance);
        }
    }

    private record ArrayNode(List<ArrayPart> parts) implements JsonNode {
        @Override
        public String format(int indent) {
            if (parts.isEmpty()) {
                return "[]";
            }

            var output = new StringBuilder("[\n");
            for (var i = 0; i < parts.size(); i++) {
                var part = parts.get(i);
                if (part instanceof CommentPart comment) {
                    output.append(indent(indent + 1)).append(comment.text()).append('\n');
                    continue;
                }

                var item = (ItemPart) part;
                var line = indent(indent + 1) + item.value().format(indent + 1);
                if (hasLaterItem(parts, i)) {
                    line = appendComma(line);
                }
                output.append(line).append('\n');
            }
            output.append(indent(indent)).append(']');
            return output.toString();
        }

        private boolean hasLaterItem(List<ArrayPart> parts, int index) {
            return parts.subList(index + 1, parts.size()).stream().anyMatch(ItemPart.class::isInstance);
        }
    }

    private record EntryPart(String key, JsonNode value) implements ObjectPart {
    }

    private record ItemPart(JsonNode value) implements ArrayPart {
    }

    private record CommentPart(String text) implements ObjectPart, ArrayPart {
    }

    private static String appendComma(String text) {
        return text + ",";
    }

    private static String indent(int size) {
        return "  ".repeat(Math.max(0, size));
    }

    private static final class Tokenizer {
        private final String source;
        private int index;
        private boolean lineHasOnlyWhitespace = true;

        private Tokenizer(String source) {
            this.source = source.replace("\r\n", "\n").replace('\r', '\n');
        }

        private List<Token> tokenize() throws BodyException {
            var tokens = new ArrayList<Token>();
            while (!isAtEnd()) {
                var current = source.charAt(index);
                if (current == '\n') {
                    lineHasOnlyWhitespace = true;
                    index++;
                    continue;
                }
                if (Character.isWhitespace(current)) {
                    index++;
                    continue;
                }
                if (current == '#') {
                    if (!lineHasOnlyWhitespace) {
                        throw new BodyException("Inline JSON comments are not supported; use a full-line # comment");
                    }
                    tokens.add(readComment());
                    continue;
                }

                lineHasOnlyWhitespace = false;
                switch (current) {
                    case '{' -> tokens.add(single(TokenType.LEFT_BRACE));
                    case '}' -> tokens.add(single(TokenType.RIGHT_BRACE));
                    case '[' -> tokens.add(single(TokenType.LEFT_BRACKET));
                    case ']' -> tokens.add(single(TokenType.RIGHT_BRACKET));
                    case ':' -> tokens.add(single(TokenType.COLON));
                    case ',' -> tokens.add(single(TokenType.COMMA));
                    case '"' -> tokens.add(readString());
                    default -> {
                        if (current == '-' || Character.isDigit(current)) {
                            tokens.add(readNumber());
                        } else if (Character.isLetter(current)) {
                            tokens.add(readLiteral());
                        } else {
                            throw new BodyException("Unexpected JSON character: " + current);
                        }
                    }
                }
            }
            tokens.add(new Token(TokenType.EOF, ""));
            return tokens;
        }

        private Token single(TokenType type) {
            return new Token(type, Character.toString(source.charAt(index++)));
        }

        private Token readComment() {
            var start = index;
            while (!isAtEnd() && source.charAt(index) != '\n') {
                index++;
            }
            return new Token(TokenType.COMMENT, source.substring(start, index).trim());
        }

        private Token readString() throws BodyException {
            var start = index++;
            var escaped = false;
            while (!isAtEnd()) {
                var current = source.charAt(index++);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (current == '\\') {
                    escaped = true;
                    continue;
                }
                if (current == '"') {
                    return new Token(TokenType.STRING, source.substring(start, index));
                }
                if (current < 0x20) {
                    throw new BodyException("Unescaped control character in JSON string");
                }
            }
            throw new BodyException("Unterminated JSON string");
        }

        private Token readNumber() {
            var start = index;
            while (!isAtEnd() && "-+0123456789.eE".indexOf(source.charAt(index)) != -1) {
                index++;
            }
            return new Token(TokenType.NUMBER, source.substring(start, index));
        }

        private Token readLiteral() throws BodyException {
            var start = index;
            while (!isAtEnd() && Character.isLetter(source.charAt(index))) {
                index++;
            }
            var literal = source.substring(start, index);
            if (!literal.equals("true") && !literal.equals("false") && !literal.equals("null")) {
                throw new BodyException("Invalid JSON literal: " + literal);
            }
            return new Token(TokenType.LITERAL, literal);
        }

        private boolean isAtEnd() {
            return index >= source.length();
        }
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int index;

        private Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        private String parse() throws BodyException {
            var leadingComments = readComments();
            var value = parseValue();
            var trailingComments = readComments();
            consume(TokenType.EOF, "unexpected JSON content");

            var output = new StringBuilder();
            for (var comment : leadingComments) {
                output.append(comment).append('\n');
            }
            output.append(value.format(0));
            for (var comment : trailingComments) {
                output.append('\n').append(comment);
            }
            return output.toString();
        }

        private JsonNode parseValue() throws BodyException {
            return switch (peek().type()) {
                case LEFT_BRACE -> parseObject();
                case LEFT_BRACKET -> parseArray();
                case STRING, NUMBER, LITERAL -> new ScalarNode(advance().text());
                default -> throw error("expected JSON value");
            };
        }

        private JsonNode parseObject() throws BodyException {
            consume(TokenType.LEFT_BRACE, "expected object");
            var parts = new ArrayList<ObjectPart>();
            if (tryConsume(TokenType.RIGHT_BRACE)) {
                return new ObjectNode(parts);
            }

            while (true) {
                while (peek().type() == TokenType.COMMENT) {
                    parts.add(new CommentPart(advance().text()));
                }
                if (tryConsume(TokenType.RIGHT_BRACE)) {
                    return new ObjectNode(parts);
                }

                var key = consume(TokenType.STRING, "expected object key string").text();
                consume(TokenType.COLON, "expected ':' after object key");
                parts.add(new EntryPart(key, parseValue()));

                if (tryConsume(TokenType.COMMA)) {
                    continue;
                }
                consume(TokenType.RIGHT_BRACE, "expected ',' or '}' after object entry");
                return new ObjectNode(parts);
            }
        }

        private JsonNode parseArray() throws BodyException {
            consume(TokenType.LEFT_BRACKET, "expected array");
            var parts = new ArrayList<ArrayPart>();
            if (tryConsume(TokenType.RIGHT_BRACKET)) {
                return new ArrayNode(parts);
            }

            while (true) {
                while (peek().type() == TokenType.COMMENT) {
                    parts.add(new CommentPart(advance().text()));
                }
                if (tryConsume(TokenType.RIGHT_BRACKET)) {
                    return new ArrayNode(parts);
                }

                parts.add(new ItemPart(parseValue()));

                if (tryConsume(TokenType.COMMA)) {
                    continue;
                }
                consume(TokenType.RIGHT_BRACKET, "expected ',' or ']' after array item");
                return new ArrayNode(parts);
            }
        }

        private List<String> readComments() {
            var comments = new ArrayList<String>();
            while (peek().type() == TokenType.COMMENT) {
                comments.add(advance().text());
            }
            return comments;
        }

        private boolean tryConsume(TokenType type) {
            if (peek().type() == type) {
                index++;
                return true;
            }
            return false;
        }

        private Token consume(TokenType type, String message) throws BodyException {
            if (peek().type() != type) {
                throw error(message);
            }
            return advance();
        }

        private Token advance() {
            return tokens.get(index++);
        }

        private Token peek() {
            return tokens.get(index);
        }

        private BodyException error(String message) {
            return new BodyException(message + " near token " + peek().text());
        }
    }
}
