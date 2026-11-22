package sh.harold.library.reload.core;

import sh.harold.library.reload.DataDecodeException;
import sh.harold.library.reload.JsonValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small strict JSON parser (RFC 8259) that records each value's JSONPath and line, so decoders can name the key and
 * field of bad data. Duplicate keys are an error. No dependencies, so it stays host-free.
 */
public final class JsonReader {

    private final String text;
    private int pos;
    private int line = 1;

    private JsonReader(String text) {
        this.text = text;
    }

    /** Parses one JSON document. Throws {@link DataDecodeException} with the line of the first syntax error. */
    public static JsonValue parse(String text) {
        JsonReader reader = new JsonReader(text.startsWith("﻿") ? text.substring(1) : text);
        reader.skipWhitespace();
        JsonValue value = reader.readValue("$");
        reader.skipWhitespace();
        if (reader.pos < reader.text.length()) {
            throw reader.error("$", "unexpected text after the document");
        }
        return value;
    }

    private JsonValue readValue(String path) {
        if (pos >= text.length()) {
            throw error(path, "unexpected end of file");
        }
        char c = text.charAt(pos);
        int startLine = line;
        return switch (c) {
            case '{' -> readObject(path);
            case '[' -> readArray(path);
            case '"' -> new JsonValue.JsonString(readString(path), path, startLine);
            case 't' -> literal(path, "true", new JsonValue.JsonBoolean(true, path, startLine));
            case 'f' -> literal(path, "false", new JsonValue.JsonBoolean(false, path, startLine));
            case 'n' -> literal(path, "null", new JsonValue.JsonNull(path, startLine));
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield new JsonValue.JsonNumber(readNumber(path), path, startLine);
                }
                throw error(path, "unexpected character '" + c + "'");
            }
        };
    }

    private JsonValue readObject(String path) {
        int startLine = line;
        pos++;
        Map<String, JsonValue> members = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return new JsonValue.JsonObject(members, path, startLine);
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error(path, "expected a quoted key");
            }
            String key = readString(path);
            String memberPath = path + (isPlainKey(key) ? "." + key : "['" + key.replace("'", "\\'") + "']");
            skipWhitespace();
            expect(':', memberPath);
            skipWhitespace();
            JsonValue value = readValue(memberPath);
            if (members.put(key, value) != null) {
                throw error(memberPath, "duplicate key '" + key + "'");
            }
            skipWhitespace();
            char c = peek();
            pos++;
            if (c == '}') {
                return new JsonValue.JsonObject(members, path, startLine);
            }
            if (c != ',') {
                pos--;
                throw error(path, "expected ',' or '}'");
            }
        }
    }

    private JsonValue readArray(String path) {
        int startLine = line;
        pos++;
        List<JsonValue> elements = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return new JsonValue.JsonArray(elements, path, startLine);
        }
        while (true) {
            skipWhitespace();
            elements.add(readValue(path + "[" + elements.size() + "]"));
            skipWhitespace();
            char c = peek();
            pos++;
            if (c == ']') {
                return new JsonValue.JsonArray(elements, path, startLine);
            }
            if (c != ',') {
                pos--;
                throw error(path, "expected ',' or ']'");
            }
        }
    }

    private String readString(String path) {
        pos++;
        StringBuilder out = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw error(path, "unterminated string");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\n' || c < 0x20) {
                throw error(path, "control character in a string");
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw error(path, "unterminated string");
            }
            char escape = text.charAt(pos++);
            switch (escape) {
                case '"', '\\', '/' -> out.append(escape);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) {
                        throw error(path, "bad unicode escape");
                    }
                    try {
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException e) {
                        throw error(path, "bad unicode escape");
                    }
                    pos += 4;
                }
                default -> throw error(path, "bad escape '\\" + escape + "'");
            }
        }
    }

    private double readNumber(String path) {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        if (peek() == '0') {
            pos++;
        } else if (isDigit(peek())) {
            while (isDigit(peek())) {
                pos++;
            }
        } else {
            throw error(path, "bad number");
        }
        if (peek() == '.') {
            pos++;
            if (!isDigit(peek())) {
                throw error(path, "bad number");
            }
            while (isDigit(peek())) {
                pos++;
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            pos++;
            if (peek() == '+' || peek() == '-') {
                pos++;
            }
            if (!isDigit(peek())) {
                throw error(path, "bad number");
            }
            while (isDigit(peek())) {
                pos++;
            }
        }
        return Double.parseDouble(text.substring(start, pos));
    }

    private JsonValue literal(String path, String word, JsonValue value) {
        if (!text.startsWith(word, pos)) {
            throw error(path, "unexpected token");
        }
        pos += word.length();
        return value;
    }

    private void expect(char c, String path) {
        if (peek() != c) {
            throw error(path, "expected '" + c + "'");
        }
        pos++;
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == '\n') {
                line++;
            } else if (c != ' ' && c != '\t' && c != '\r') {
                return;
            }
            pos++;
        }
    }

    private DataDecodeException error(String path, String reason) {
        return new DataDecodeException(path, line, "invalid JSON: " + reason);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isPlainKey(String key) {
        if (key.isEmpty() || !Character.isJavaIdentifierStart(key.charAt(0))) {
            return false;
        }
        for (int i = 1; i < key.length(); i++) {
            if (!Character.isJavaIdentifierPart(key.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
