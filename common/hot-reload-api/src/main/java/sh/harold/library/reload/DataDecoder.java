package sh.harold.library.reload;

/**
 * Turns a parsed data file into a value. A decoder throws {@link DataDecodeException} for bad data, so the error names
 * the file, the key and the field, and the last good value stays live.
 */
@FunctionalInterface
public interface DataDecoder<T> {

    T decode(JsonValue root);
}
