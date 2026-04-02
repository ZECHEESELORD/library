package sh.harold.creative.library.data;

import java.util.Objects;
import java.util.Optional;

public final class WriteCondition {

    private static final WriteCondition NONE = new WriteCondition(null, null);

    private final Boolean expectedExists;
    private final String expectedRevision;

    private WriteCondition(Boolean expectedExists, String expectedRevision) {
        this.expectedExists = expectedExists;
        this.expectedRevision = expectedRevision;
    }

    public static WriteCondition none() {
        return NONE;
    }

    public static WriteCondition exists() {
        return new WriteCondition(Boolean.TRUE, null);
    }

    public static WriteCondition notExists() {
        return new WriteCondition(Boolean.FALSE, null);
    }

    public static WriteCondition revision(String expectedRevision) {
        return new WriteCondition(null, requireRevision(expectedRevision));
    }

    public WriteCondition requireExists() {
        return new WriteCondition(Boolean.TRUE, expectedRevision);
    }

    public WriteCondition requireNotExists() {
        return new WriteCondition(Boolean.FALSE, expectedRevision);
    }

    public WriteCondition withExpectedRevision(String revision) {
        return new WriteCondition(expectedExists, requireRevision(revision));
    }

    public Optional<Boolean> expectedExists() {
        return Optional.ofNullable(expectedExists);
    }

    public Optional<String> expectedRevision() {
        return Optional.ofNullable(expectedRevision);
    }

    public boolean matches(DocumentSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (expectedExists != null && expectedExists.booleanValue() != snapshot.exists()) {
            return false;
        }
        return expectedRevision == null || expectedRevision.equals(snapshot.revision());
    }

    private static String requireRevision(String value) {
        Objects.requireNonNull(value, "expectedRevision");
        if (value.isBlank()) {
            throw new IllegalArgumentException("expectedRevision cannot be blank");
        }
        return value;
    }
}
