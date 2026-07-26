package sh.harold.library.npc.behavior.core;

import sh.harold.library.npc.behavior.NpcAttentionLease;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

final class StandardNpcAttentionLease implements NpcAttentionLease {

    private final UUID viewerId;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final Runnable close;

    StandardNpcAttentionLease(UUID viewerId, Runnable close) {
        this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
        this.close = Objects.requireNonNull(close, "close");
    }

    @Override
    public UUID viewerId() {
        return viewerId;
    }

    @Override
    public boolean active() {
        return active.get();
    }

    @Override
    public void close() {
        if (active.compareAndSet(true, false)) {
            close.run();
        }
    }
}
