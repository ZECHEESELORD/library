package sh.harold.library.reload;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A parsed JSON value that knows where it came from: its JSONPath ({@code $.moves.roll.fov}) and 1-based line. Decoders
 * read data through the typed accessors, which fail with a {@link DataDecodeException} naming the key and the field.
 */
public sealed interface JsonValue
        permits JsonValue.JsonObject, JsonValue.JsonArray, JsonValue.JsonString, JsonValue.JsonNumber,
        JsonValue.JsonBoolean, JsonValue.JsonNull {

    String path();

    int line();

    default JsonObject asObject() {
        if (this instanceof JsonObject object) {
            return object;
        }
        throw mismatch("an object");
    }

    default JsonArray asArray() {
        if (this instanceof JsonArray array) {
            return array;
        }
        throw mismatch("an array");
    }

    default String asString() {
        if (this instanceof JsonString string) {
            return string.value();
        }
        throw mismatch("a string");
    }

    default double asDouble() {
        if (this instanceof JsonNumber number) {
            return number.value();
        }
        throw mismatch("a number");
    }

    default int asInt() {
        double value = asDouble();
        if (value != Math.rint(value) || Math.abs(value) > Integer.MAX_VALUE) {
            throw new DataDecodeException(this, "expected a whole number, found " + value);
        }
        return (int) value;
    }

    default boolean asBoolean() {
        if (this instanceof JsonBoolean bool) {
            return bool.value();
        }
        throw mismatch("true or false");
    }

    private DataDecodeException mismatch(String expected) {
        String found = switch (this) {
            case JsonObject object -> "an object";
            case JsonArray array -> "an array";
            case JsonString string -> "the string \"" + string.value() + "\"";
            case JsonNumber number -> "the number " + number.value();
            case JsonBoolean bool -> String.valueOf(bool.value());
            case JsonNull nul -> "null";
        };
        return new DataDecodeException(this, "expected " + expected + ", found " + found);
    }

    record JsonObject(Map<String, JsonValue> members, String path, int line) implements JsonValue {
        public JsonObject {
            members = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                    Objects.requireNonNull(members, "members")));
        }

        /** A required member. */
        public JsonValue get(String name) {
            JsonValue value = members.get(name);
            if (value == null) {
                throw new DataDecodeException(path, line, "missing field '" + name + "'");
            }
            return value;
        }

        public Optional<JsonValue> optional(String name) {
            JsonValue value = members.get(name);
            return value == null || value instanceof JsonNull ? Optional.empty() : Optional.of(value);
        }

        public boolean has(String name) {
            return optional(name).isPresent();
        }

        public double getDouble(String name, double fallback) {
            return optional(name).map(JsonValue::asDouble).orElse(fallback);
        }

        public String getString(String name, String fallback) {
            return optional(name).map(JsonValue::asString).orElse(fallback);
        }

        public boolean getBoolean(String name, boolean fallback) {
            return optional(name).map(JsonValue::asBoolean).orElse(fallback);
        }
    }

    record JsonArray(List<JsonValue> elements, String path, int line) implements JsonValue {
        public JsonArray {
            elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        }
    }

    record JsonString(String value, String path, int line) implements JsonValue {
        public JsonString {
            Objects.requireNonNull(value, "value");
        }
    }

    record JsonNumber(double value, String path, int line) implements JsonValue {
    }

    record JsonBoolean(boolean value, String path, int line) implements JsonValue {
    }

    record JsonNull(String path, int line) implements JsonValue {
    }
}
