package sh.harold.library.movement.core.tuning;

import sh.harold.library.reload.DataDecodeException;
import sh.harold.library.reload.JsonValue;
import sh.harold.library.reload.JsonValue.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Strict readers for tuning files: every field is required, so a typo fails loudly with its path and line. */
final class Json {

    private Json() {
    }

    static JsonObject object(JsonObject parent, String name) {
        return parent.get(name).asObject();
    }

    static double number(JsonObject object, String name) {
        JsonValue value = object.get(name);
        double number = value.asDouble();
        if (!Double.isFinite(number)) {
            throw new DataDecodeException(value, name + " must be finite");
        }
        return number;
    }

    static double positive(JsonObject object, String name) {
        double number = number(object, name);
        if (!(number > 0.0)) {
            throw new DataDecodeException(object.get(name), name + " must be positive");
        }
        return number;
    }

    static double nonNegative(JsonObject object, String name) {
        double number = number(object, name);
        if (number < 0.0) {
            throw new DataDecodeException(object.get(name), name + " must not be negative");
        }
        return number;
    }

    static int ticks(JsonObject object, String name) {
        int value = object.get(name).asInt();
        if (value < 0) {
            throw new DataDecodeException(object.get(name), name + " must not be negative");
        }
        return value;
    }

    static String string(JsonObject object, String name) {
        return object.get(name).asString();
    }

    static boolean bool(JsonObject object, String name) {
        return object.get(name).asBoolean();
    }

    static List<Double> numbers(JsonObject object, String name) {
        List<Double> values = new ArrayList<>();
        for (JsonValue element : object.get(name).asArray().elements()) {
            values.add(element.asDouble());
        }
        if (values.isEmpty()) {
            throw new DataDecodeException(object.get(name), name + " must not be empty");
        }
        return List.copyOf(values);
    }

    static List<Integer> integers(JsonObject object, String name) {
        List<Integer> values = new ArrayList<>();
        for (JsonValue element : object.get(name).asArray().elements()) {
            values.add(element.asInt());
        }
        return List.copyOf(values);
    }
}
