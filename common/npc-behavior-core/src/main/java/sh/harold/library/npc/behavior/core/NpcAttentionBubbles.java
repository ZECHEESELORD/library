package sh.harold.library.npc.behavior.core;

import net.kyori.adventure.text.Component;
import sh.harold.library.npc.behavior.NpcVoiceProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Hybrid real/virtual attention-bark compositor. */
public final class NpcAttentionBubbles {

    private final NpcBehaviorRenderPort renderer;
    private final NpcBehaviorRandom random;
    private final Map<UUID, NpcBubbleFrame> virtual = new LinkedHashMap<>();
    private long nextId = 1L << 40;
    private Real real;

    public NpcAttentionBubbles(NpcBehaviorRenderPort renderer, NpcBehaviorRandom random) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.random = Objects.requireNonNull(random, "random");
    }

    public synchronized NpcBubbleFrame show(
            UUID target,
            Component text,
            NpcVoiceProfile voice,
            Set<UUID> trackedViewers,
            Set<UUID> engagedViewers,
            long tick
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(voice, "voice");
        trackedViewers = Set.copyOf(Objects.requireNonNull(trackedViewers, "trackedViewers"));
        engagedViewers = Set.copyOf(Objects.requireNonNull(engagedViewers, "engagedViewers"));

        if (real != null) {
            if (tick < real.bubble.expiresAtTick() && engagedViewers.contains(real.target)) {
                replaceVirtual(real.target, real.bubble);
            }
            renderer.clearSharedBubble(real.bubble.id());
        }
        removeVirtual(target);

        Set<UUID> excluded = new LinkedHashSet<>(engagedViewers);
        excluded.remove(target);
        long expiresAt = tick + NpcSpeechText.holdTicks(text);
        NpcBubbleFrame bubble = new NpcBubbleFrame(
                nextId++,
                NpcSpeechText.wrap(text),
                expiresAt,
                NpcBubbleFrame.Kind.ATTENTION,
                excluded
        );
        real = new Real(target, bubble);
        renderer.showSharedBubble(bubble);

        Set<UUID> recipients = new LinkedHashSet<>(trackedViewers);
        recipients.removeAll(excluded);
        playVoice(voice, recipients);
        return bubble;
    }

    public synchronized NpcBubbleFrame showViewer(
            UUID target,
            Component text,
            NpcVoiceProfile voice,
            long tick
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(voice, "voice");
        clearViewer(target);
        long expiresAt = tick + NpcSpeechText.holdTicks(text);
        NpcBubbleFrame bubble = new NpcBubbleFrame(
                nextId++,
                NpcSpeechText.wrap(text),
                expiresAt,
                NpcBubbleFrame.Kind.ATTENTION
        );
        virtual.put(target, bubble);
        renderer.showVirtualBubble(target, bubble);
        playVoice(voice, Set.of(target));
        return bubble;
    }

    public synchronized void tick(long tick) {
        if (real != null && tick >= real.bubble.expiresAtTick()) {
            renderer.clearSharedBubble(real.bubble.id());
            real = null;
        }
        List<UUID> expired = new ArrayList<>();
        virtual.forEach((viewer, bubble) -> {
            if (tick >= bubble.expiresAtTick()) {
                expired.add(viewer);
            }
        });
        expired.forEach(this::removeVirtual);
    }

    public synchronized void release(UUID viewerId) {
        Objects.requireNonNull(viewerId, "viewerId");
        removeVirtual(viewerId);
        if (real != null && real.bubble.excludedViewers().contains(viewerId)) {
            Set<UUID> exclusions = new LinkedHashSet<>(real.bubble.excludedViewers());
            exclusions.remove(viewerId);
            NpcBubbleFrame updated = new NpcBubbleFrame(
                    real.bubble.id(),
                    real.bubble.text(),
                    real.bubble.expiresAtTick(),
                    real.bubble.kind(),
                    exclusions
            );
            real = new Real(real.target, updated);
            renderer.showSharedBubble(updated);
            return;
        }
        if (real != null && real.target.equals(viewerId)) {
            renderer.clearSharedBubble(real.bubble.id());
            real = null;
        }
    }

    public synchronized void clearViewer(UUID viewerId) {
        Objects.requireNonNull(viewerId, "viewerId");
        removeVirtual(viewerId);
        if (real != null && !real.bubble.excludedViewers().contains(viewerId)) {
            Set<UUID> exclusions = new LinkedHashSet<>(real.bubble.excludedViewers());
            exclusions.add(viewerId);
            NpcBubbleFrame updated = new NpcBubbleFrame(
                    real.bubble.id(),
                    real.bubble.text(),
                    real.bubble.expiresAtTick(),
                    real.bubble.kind(),
                    exclusions
            );
            real = new Real(real.target, updated);
            renderer.showSharedBubble(updated);
        }
    }

    public synchronized void clear() {
        clearExcept(Set.of());
    }

    public synchronized void clearExcept(Set<UUID> retainedViewers) {
        Set<UUID> retained = Set.copyOf(Objects.requireNonNull(retainedViewers, "retainedViewers"));
        if (real != null) {
            renderer.clearSharedBubble(real.bubble.id());
            real = null;
        }
        List<UUID> viewers = virtual.keySet().stream()
                .filter(viewer -> !retained.contains(viewer))
                .toList();
        viewers.forEach(this::removeVirtual);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                Optional.ofNullable(real).map(value -> value.target),
                Optional.ofNullable(real).map(value -> value.bubble),
                Map.copyOf(virtual)
        );
    }

    private void replaceVirtual(UUID viewerId, NpcBubbleFrame source) {
        removeVirtual(viewerId);
        NpcBubbleFrame copy = new NpcBubbleFrame(
                source.id(),
                source.text(),
                source.expiresAtTick(),
                source.kind()
        );
        virtual.put(viewerId, copy);
        renderer.showVirtualBubble(viewerId, copy);
    }

    private void removeVirtual(UUID viewerId) {
        NpcBubbleFrame removed = virtual.remove(viewerId);
        if (removed != null) {
            renderer.clearVirtualBubble(viewerId, removed.id());
        }
    }

    private void playVoice(NpcVoiceProfile voice, Set<UUID> recipients) {
        NpcVoiceDelivery.select(voice, random).ifPresent(cue -> recipients.forEach(recipient ->
                renderer.playSound(cue.viewer(recipient))
        ));
    }

    public record Snapshot(
            Optional<UUID> realTarget,
            Optional<NpcBubbleFrame> realBubble,
            Map<UUID, NpcBubbleFrame> virtualBubbles
    ) {
        public Snapshot {
            realTarget = Objects.requireNonNull(realTarget, "realTarget");
            realBubble = Objects.requireNonNull(realBubble, "realBubble");
            virtualBubbles = Map.copyOf(Objects.requireNonNull(virtualBubbles, "virtualBubbles"));
        }
    }

    private record Real(UUID target, NpcBubbleFrame bubble) {
    }
}
