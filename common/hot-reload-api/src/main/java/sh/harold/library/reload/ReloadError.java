package sh.harold.library.reload;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Why a data file did not load: the file, the JSONPath of the key and field, the 1-based line (0 when unknown) and the
 * reason. The previous good data stays live.
 */
public record ReloadError(Path file, String path, int line, String reason) {

    public ReloadError {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(reason, "reason");
    }

    public static ReloadError of(Path file, DataDecodeException exception) {
        return new ReloadError(file, exception.path(), exception.line(), exception.reason());
    }

    /** One line for a log or chat: {@code roll.json: $.fov.amount (line 7): expected a number, found "six"}. */
    public String describe() {
        return file.getFileName() + ": " + path + (line > 0 ? " (line " + line + ")" : "") + ": " + reason;
    }
}
