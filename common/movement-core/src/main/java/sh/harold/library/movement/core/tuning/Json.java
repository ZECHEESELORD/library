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

    /** A list of {@code [tick, value]} pairs with ticks rising. */
    static List<TickKey> keys(JsonObject object, String name) {
        List<TickKey> keys = new ArrayList<>();
        int last = -1;
        for (JsonValue element : object.get(name).asArray().elements()) {
            List<JsonValue> pair = element.asArray().elements();
            if (pair.size() != 2) {
                throw new DataDecodeException(element, name + " entries must be [tick, value] pairs");
            }
            int tick = pair.get(0).asInt();
            double value = pair.get(1).asDouble();
            if (tick <= last || !Double.isFinite(value)) {
                throw new DataDecodeException(element, name + " ticks must rise and values be finite");
            }
            last = tick;
            keys.add(new TickKey(tick, value));
        }
        if (keys.isEmpty()) {
            throw new DataDecodeException(object.get(name), name + " must not be empty");
        }
        return List.copyOf(keys);
    }

    /** An arm pose written as {@code [x, y, z, rotX, rotY, rotZ]}. */
    static ArmPoseTuning arm(JsonObject object, String name) {
        List<Double> values = numbers(object, name);
        if (values.size() != 6) {
            throw new DataDecodeException(object.get(name), name + " must be [x, y, z, rotX, rotY, rotZ]");
        }
        return new ArmPoseTuning(values.get(0), values.get(1), values.get(2), values.get(3), values.get(4),
                values.get(5));
    }

    /** A list of {@code [tick, x, y, z, rotX, rotY, rotZ]} arm keys with ticks rising. */
    static List<ArmKey> armKeys(JsonObject object, String name) {
        List<ArmKey> keys = new ArrayList<>();
        int last = -1;
        for (JsonValue element : object.get(name).asArray().elements()) {
            List<JsonValue> values = element.asArray().elements();
            if (values.size() != 7) {
                throw new DataDecodeException(element, name + " entries must be [tick, x, y, z, rotX, rotY, rotZ]");
            }
            int tick = values.get(0).asInt();
            if (tick <= last) {
                throw new DataDecodeException(element, name + " ticks must rise");
            }
            last = tick;
            keys.add(new ArmKey(tick, new ArmPoseTuning(values.get(1).asDouble(), values.get(2).asDouble(),
                    values.get(3).asDouble(), values.get(4).asDouble(), values.get(5).asDouble(),
                    values.get(6).asDouble())));
        }
        return List.copyOf(keys);
    }

    static List<Integer> integers(JsonObject object, String name) {
        List<Integer> values = new ArrayList<>();
        for (JsonValue element : object.get(name).asArray().elements()) {
            values.add(element.asInt());
        }
        return List.copyOf(values);
    }
}
