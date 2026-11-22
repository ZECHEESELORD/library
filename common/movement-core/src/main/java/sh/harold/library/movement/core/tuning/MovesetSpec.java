package sh.harold.library.movement.core.tuning;

import net.kyori.adventure.key.Key;
import sh.harold.library.movement.JumpPreset;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.SlideVariant;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What a server sends a client so both run the same moveset: the enabled moves, the tuning file texts by file name,
 * and the jump preset and slide variant switches, which override the presets named inside {@code jump.json} and
 * {@code slide.json} when set. Missing texts fall back to the shipped defaults.
 */
public record MovesetSpec(Set<Key> enabled, Map<String, String> texts, Optional<JumpPreset> jumpPreset,
                          Optional<SlideVariant> slideVariant) {

    /** Every stock move at the shipped tuning. */
    public static final MovesetSpec DEFAULT = new MovesetSpec(Set.copyOf(MoveKeys.STOCK), Moveset.defaultTexts(),
            Optional.empty(), Optional.empty());

    public MovesetSpec {
        enabled = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(enabled, "enabled")));
        texts = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(texts, "texts")));
        Objects.requireNonNull(jumpPreset, "jumpPreset");
        Objects.requireNonNull(slideVariant, "slideVariant");
    }

    /** No moves: the host behaves exactly like vanilla. */
    public static MovesetSpec none() {
        return DEFAULT.withEnabled(Set.of());
    }

    /**
     * Decodes the moveset.
     *
     * @throws sh.harold.library.reload.DataDecodeException if a text does not decode
     */
    public Moveset moveset() {
        Moveset moveset = Moveset.decode(enabled, texts);
        if (jumpPreset.isPresent()) {
            moveset = moveset.withJumpPreset(jumpPreset.get());
        }
        if (slideVariant.isPresent()) {
            moveset = moveset.withSlideVariant(slideVariant.get());
        }
        return moveset;
    }

    public MovesetSpec withEnabled(Set<Key> next) {
        return new MovesetSpec(next, texts, jumpPreset, slideVariant);
    }

    public MovesetSpec withMove(Key move, boolean on) {
        Set<Key> next = new LinkedHashSet<>(enabled);
        if (on) {
            next.add(move);
        } else {
            next.remove(move);
        }
        return withEnabled(next);
    }

    public MovesetSpec withText(String file, String text) {
        if (!Moveset.FILES.contains(file)) {
            throw new IllegalArgumentException("unknown tuning file " + file);
        }
        Map<String, String> next = new LinkedHashMap<>(texts);
        next.put(file, Objects.requireNonNull(text, "text"));
        return new MovesetSpec(enabled, next, jumpPreset, slideVariant);
    }

    public MovesetSpec withJumpPreset(JumpPreset preset) {
        return new MovesetSpec(enabled, texts, Optional.of(preset), slideVariant);
    }

    public MovesetSpec withSlideVariant(SlideVariant variant) {
        return new MovesetSpec(enabled, texts, jumpPreset, Optional.of(variant));
    }
}
