package sh.harold.creative.library.overlay.minestom;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class MinestomFutureGuard {

    private MinestomFutureGuard() {
    }

    static <T> T requireCompleted(CompletionStage<T> stage, String operation) {
        Objects.requireNonNull(stage, "stage");
        String description = Objects.requireNonNull(operation, "operation");
        CompletableFuture<T> future = stage.toCompletableFuture();
        if (!future.isDone()) {
            throw new IllegalStateException(description + " must already be complete on the Minestom owned thread; blocking is not allowed");
        }
        return future.getNow(null);
    }
}
