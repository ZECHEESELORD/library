package sh.harold.library.npc.behavior.core;

import sh.harold.library.spatial.AnchorRef;
import sh.harold.library.spatial.AnchorSnapshot;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Actor-lane rendering boundary used by the platform-neutral behavior runtime.
 *
 * <p>Implementations must marshal viewer-specific calls onto the viewer's
 * ownership lane. The runtime never calls Bukkit, Minestom, or a protocol API
 * directly.</p>
 */
public interface NpcBehaviorRenderPort {

    default CompletionStage<Void> restoreNativePresentation() {
        return CompletableFuture.completedFuture(null);
    }

    default Optional<AnchorSnapshot> resolveAnchor(AnchorRef anchor) {
        return Optional.empty();
    }

    default void renderSharedFrame(NpcRenderFrame frame) {
    }

    default void renderViewerOverlay(UUID viewerId, NpcRenderFrame frame) {
    }

    /** Replaces an overlay with a complete current native frame. */
    default void clearViewerOverlay(UUID viewerId, NpcRenderFrame nativeFrame) {
    }

    default void showSharedBubble(NpcBubbleFrame bubble) {
    }

    default void clearSharedBubble(long bubbleId) {
    }

    default void showVirtualBubble(UUID viewerId, NpcBubbleFrame bubble) {
    }

    default void clearVirtualBubble(UUID viewerId, long bubbleId) {
    }

    default void animateShared(NpcRenderAnimation animation) {
    }

    /**
     * Emits a canonical attention act to observers and the newest target while
     * keeping earlier engaged branches isolated from that motion.
     */
    default void animateAttention(NpcRenderAnimation animation, Set<UUID> excludedViewers) {
        animateShared(animation);
    }

    default void animateViewer(UUID viewerId, NpcRenderAnimation animation) {
    }

    default void playSound(NpcRenderedSound sound) {
    }
}
