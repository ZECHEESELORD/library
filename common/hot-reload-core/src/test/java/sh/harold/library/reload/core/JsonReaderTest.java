package sh.harold.library.reload.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.reload.DataDecodeException;
import sh.harold.library.reload.JsonValue;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonReaderTest {

    @Test
    void parsesValuesWithPathsAndLines() {
        JsonValue root = JsonReader.parse("""
                {
                  "name": "roll \\u00e9\\n",
                  "amount": -1.5e1,
                  "flags": [true, false, null],
                  "nested": { "odd key": 3 }
                }
                """);
        JsonValue.JsonObject object = root.asObject();
        assertEquals("roll é\n", object.get("name").asString());
        assertEquals(-15.0, object.get("amount").asDouble());
        assertEquals("$.amount", object.get("amount").path());
        assertEquals(3, object.get("amount").line());
        List<JsonValue> flags = object.get("flags").asArray().elements();
        assertTrue(flags.get(0).asBoolean());
        assertFalse(flags.get(1).asBoolean());
        assertInstanceOf(JsonValue.JsonNull.class, flags.get(2));
        assertEquals("$.flags[2]", flags.get(2).path());
        JsonValue odd = object.get("nested").asObject().get("odd key");
        assertEquals("$.nested['odd key']", odd.path());
        assertEquals(3, odd.asInt());
        assertEquals(List.of("name", "amount", "flags", "nested"), List.copyOf(object.members().keySet()));
    }

    @Test
    void reportsSyntaxErrorsWithTheirLine() {
        DataDecodeException missingComma = assertThrows(DataDecodeException.class, () -> JsonReader.parse("""
                {
                  "a": 1
                  "b": 2
                }
                """));
        assertEquals(3, missingComma.line());

        DataDecodeException duplicate = assertThrows(DataDecodeException.class,
                () -> JsonReader.parse("{ \"a\": 1, \"a\": 2 }"));
        assertTrue(duplicate.reason().contains("duplicate"), duplicate.reason());
        assertEquals("$.a", duplicate.path());

        assertThrows(DataDecodeException.class, () -> JsonReader.parse("[1, 2,]"));
        assertThrows(DataDecodeException.class, () -> JsonReader.parse("{} trailing"));
        assertThrows(DataDecodeException.class, () -> JsonReader.parse("\"unterminated"));
        assertThrows(DataDecodeException.class, () -> JsonReader.parse(""));
    }

    @Test
    void typeMismatchesNameTheField() {
        JsonValue.JsonObject object = JsonReader.parse("{ \"fov\": { \"amount\": \"six\" } }").asObject();
        DataDecodeException error = assertThrows(DataDecodeException.class,
                () -> object.get("fov").asObject().get("amount").asDouble());
        assertEquals("$.fov.amount", error.path());
        assertTrue(error.reason().contains("expected a number"), error.reason());

        DataDecodeException missing = assertThrows(DataDecodeException.class,
                () -> object.get("fov").asObject().get("ticks"));
        assertTrue(missing.reason().contains("ticks"));
        assertEquals("$.fov", missing.path());
    }
}
