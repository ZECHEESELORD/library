package sh.harold.library.movement.core.tuning;

import sh.harold.library.reload.JsonValue;

import java.util.Map;

/**
 * Writes a parsed {@link JsonValue} back to compact JSON text, so a hot-reloaded tuning file can be sent to clients
 * exactly as the server decoded it.
 */
public final class JsonText {

    private JsonText() {
    }

    public static String write(JsonValue value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(JsonValue value, StringBuilder out) {
        switch (value) {
            case JsonValue.JsonObject object -> {
                out.append('{');
                boolean first = true;
                for (Map.Entry<String, JsonValue> member : object.members().entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    string(member.getKey(), out);
                    out.append(':');
                    write(member.getValue(), out);
                }
                out.append('}');
            }
            case JsonValue.JsonArray array -> {
                out.append('[');
                for (int i = 0; i < array.elements().size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    write(array.elements().get(i), out);
                }
                out.append(']');
            }
            case JsonValue.JsonString string -> string(string.value(), out);
            case JsonValue.JsonNumber number -> number(number.value(), out);
            case JsonValue.JsonBoolean bool -> out.append(bool.value());
            case JsonValue.JsonNull ignored -> out.append("null");
        }
    }

    private static void number(double value, StringBuilder out) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0e15) {
            out.append((long) value);
        } else {
            out.append(value);
        }
    }

    private static void string(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
