package sh.harold.library.reload;

/**
 * A data file that does not decode. {@code path} names the key and field in JSONPath form ({@code $.curves[0].channel})
 * and {@code line} the 1-based line it starts on, or 0 when unknown.
 */
public final class DataDecodeException extends RuntimeException {

    private final String path;
    private final int line;
    private final String reason;

    public DataDecodeException(String path, int line, String reason) {
        super(path + (line > 0 ? " (line " + line + ")" : "") + ": " + reason);
        this.path = path;
        this.line = line;
        this.reason = reason;
    }

    public DataDecodeException(JsonValue at, String reason) {
        this(at.path(), at.line(), reason);
    }

    public String path() {
        return path;
    }

    public int line() {
        return line;
    }

    public String reason() {
        return reason;
    }
}
