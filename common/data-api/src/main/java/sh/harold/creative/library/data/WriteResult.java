package sh.harold.creative.library.data;

import java.util.Objects;
import java.util.Optional;

public final class WriteResult {

    private final WriteStatus status;
    private final DocumentSnapshot snapshot;

    private WriteResult(WriteStatus status, DocumentSnapshot snapshot) {
        this.status = Objects.requireNonNull(status, "status");
        this.snapshot = snapshot;
    }

    public static WriteResult applied(DocumentSnapshot snapshot) {
        return new WriteResult(WriteStatus.APPLIED, snapshot);
    }

    public static WriteResult appliedWithoutSnapshot() {
        return new WriteResult(WriteStatus.APPLIED, null);
    }

    public static WriteResult conditionFailed(DocumentSnapshot snapshot) {
        return new WriteResult(WriteStatus.CONDITION_FAILED, snapshot);
    }

    public static WriteResult conditionFailedWithoutSnapshot() {
        return new WriteResult(WriteStatus.CONDITION_FAILED, null);
    }

    public WriteStatus status() {
        return status;
    }

    public boolean applied() {
        return status == WriteStatus.APPLIED;
    }

    public Optional<DocumentSnapshot> snapshot() {
        return Optional.ofNullable(snapshot);
    }
}
