package sh.harold.library.npc.behavior;

import java.util.UUID;

public interface NpcAttentionLease extends AutoCloseable {
    UUID viewerId();

    boolean active();

    @Override
    void close();
}
