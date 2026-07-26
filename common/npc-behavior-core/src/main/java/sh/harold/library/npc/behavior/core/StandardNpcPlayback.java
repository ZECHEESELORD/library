package sh.harold.library.npc.behavior.core;

import sh.harold.library.npc.behavior.NpcPlayback;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

final class StandardNpcPlayback implements NpcPlayback {

    private final UUID id = UUID.randomUUID();
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private final Runnable cancellation;

    StandardNpcPlayback(Runnable cancellation) {
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
    }

    void bind(CompletionStage<Boolean> internalCompletion) {
        Objects.requireNonNull(internalCompletion, "internalCompletion").whenComplete((ignored, failure) -> {
            active.set(false);
            if (failure == null) {
                completion.complete(null);
            } else {
                completion.completeExceptionally(failure);
            }
        });
    }

    void completeExceptionally(Throwable failure) {
        active.set(false);
        completion.completeExceptionally(failure);
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public boolean active() {
        return active.get();
    }

    @Override
    public CompletionStage<Void> completion() {
        return completion;
    }

    @Override
    public void cancel() {
        if (active.compareAndSet(true, false)) {
            cancellation.run();
        }
    }
}
