package sh.harold.library.menu.showcase;

import java.util.Objects;

public record SourceItemReference(String sha256, int slot) {

    public SourceItemReference {
        sha256 = requireSha256(sha256, "sha256");
        if (slot < 0 || slot > 53) {
            throw new IllegalArgumentException("slot must be between 0 and 53");
        }
    }

    static String requireSha256(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be a full lowercase SHA-256 value");
        }
        return value;
    }
}
