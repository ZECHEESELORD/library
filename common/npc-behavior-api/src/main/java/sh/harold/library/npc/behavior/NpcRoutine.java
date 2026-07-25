package sh.harold.library.npc.behavior;

import net.kyori.adventure.key.Key;
import sh.harold.library.entity.EquipmentSlot;
import sh.harold.library.entity.InteractionHand;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.spatial.AnchorRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable sequence of declarative, non-locomotion mannequin actions.
 */
public final class NpcRoutine {
    private final Key key;
    private final List<NpcRoutineStep> steps;

    private NpcRoutine(Key key, List<NpcRoutineStep> steps) {
        this.key = Objects.requireNonNull(key, "key");
        this.steps = List.copyOf(steps);
        if (this.steps.isEmpty()) {
            throw new IllegalArgumentException("A routine needs at least one step");
        }
    }

    public Key key() {
        return key;
    }

    public List<NpcRoutineStep> steps() {
        return steps;
    }

    public static Builder builder(Key key) {
        return new Builder(key);
    }

    public static final class Builder {
        private final Key key;
        private final List<NpcRoutineStep> steps = new ArrayList<>();

        private Builder(Key key) {
            this.key = Objects.requireNonNull(key, "key");
        }

        public Builder lookAt(AnchorRef anchor, NpcTimingBand timing) {
            return step(new NpcRoutineStep.LookAt(anchor, timing));
        }

        public Builder sweep(AnchorRef from, AnchorRef to, NpcTimingBand timing) {
            return step(new NpcRoutineStep.Sweep(from, to, timing));
        }

        public Builder stance(NpcStance stance) {
            return step(new NpcRoutineStep.Stance(stance));
        }

        public Builder equip(EquipmentSlot slot, ItemDescriptor item) {
            return step(new NpcRoutineStep.Equip(slot, item));
        }

        public Builder equipOneOf(EquipmentSlot slot, Collection<ItemDescriptor> items) {
            return step(new NpcRoutineStep.EquipOneOf(slot, List.copyOf(items)));
        }

        public Builder clear(EquipmentSlot slot) {
            return step(new NpcRoutineStep.Clear(slot));
        }

        public Builder gesture(NpcGesturePreset gesture) {
            return step(new NpcRoutineStep.Gesture(gesture, Optional.empty()));
        }

        public Builder gesture(NpcGesturePreset gesture, NpcSoundProfile sound) {
            return step(new NpcRoutineStep.Gesture(gesture, Optional.of(Objects.requireNonNull(sound, "sound"))));
        }

        public Builder swing(InteractionHand hand) {
            return step(new NpcRoutineStep.Swing(hand, Optional.empty()));
        }

        public Builder swing(InteractionHand hand, NpcSoundProfile sound) {
            return step(new NpcRoutineStep.Swing(hand, Optional.of(Objects.requireNonNull(sound, "sound"))));
        }

        public Builder useItem(InteractionHand hand, NpcTimingBand timing) {
            return step(new NpcRoutineStep.UseItem(hand, timing, Optional.empty()));
        }

        public Builder useItem(InteractionHand hand, NpcTimingBand timing, NpcSoundProfile sound) {
            return step(new NpcRoutineStep.UseItem(
                    hand,
                    timing,
                    Optional.of(Objects.requireNonNull(sound, "sound"))
            ));
        }

        public Builder sound(NpcSoundProfile sound) {
            return step(new NpcRoutineStep.Sound(sound));
        }

        public Builder wait(NpcTimingBand timing) {
            return step(new NpcRoutineStep.Wait(timing));
        }

        public Builder step(NpcRoutineStep step) {
            steps.add(Objects.requireNonNull(step, "step"));
            return this;
        }

        public NpcRoutine build() {
            return new NpcRoutine(key, steps);
        }
    }
}
