package sh.harold.library.npc.behavior;

import sh.harold.library.entity.EquipmentSlot;
import sh.harold.library.entity.InteractionHand;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.spatial.AnchorRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public sealed interface NpcRoutineStep permits
        NpcRoutineStep.LookAt,
        NpcRoutineStep.Sweep,
        NpcRoutineStep.Stance,
        NpcRoutineStep.Equip,
        NpcRoutineStep.EquipOneOf,
        NpcRoutineStep.Clear,
        NpcRoutineStep.Gesture,
        NpcRoutineStep.Swing,
        NpcRoutineStep.UseItem,
        NpcRoutineStep.Sound,
        NpcRoutineStep.Wait {

    record LookAt(AnchorRef anchor, NpcTimingBand timing) implements NpcRoutineStep {
        public LookAt {
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(timing, "timing");
        }
    }

    record Sweep(AnchorRef from, AnchorRef to, NpcTimingBand timing) implements NpcRoutineStep {
        public Sweep {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            Objects.requireNonNull(timing, "timing");
        }
    }

    record Stance(NpcStance stance) implements NpcRoutineStep {
        public Stance {
            Objects.requireNonNull(stance, "stance");
        }
    }

    record Equip(EquipmentSlot slot, ItemDescriptor item) implements NpcRoutineStep {
        public Equip {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(item, "item");
        }
    }

    record EquipOneOf(EquipmentSlot slot, List<ItemDescriptor> items) implements NpcRoutineStep {
        public EquipOneOf {
            Objects.requireNonNull(slot, "slot");
            items = List.copyOf(Objects.requireNonNull(items, "items"));
            if (items.isEmpty()) {
                throw new IllegalArgumentException("items cannot be empty");
            }
        }
    }

    record Clear(EquipmentSlot slot) implements NpcRoutineStep {
        public Clear {
            Objects.requireNonNull(slot, "slot");
        }
    }

    record Gesture(NpcGesturePreset gesture, Optional<NpcSoundProfile> sound) implements NpcRoutineStep {
        public Gesture {
            Objects.requireNonNull(gesture, "gesture");
            sound = Objects.requireNonNull(sound, "sound");
        }
    }

    record Swing(InteractionHand hand, Optional<NpcSoundProfile> sound) implements NpcRoutineStep {
        public Swing {
            Objects.requireNonNull(hand, "hand");
            sound = Objects.requireNonNull(sound, "sound");
        }
    }

    record UseItem(
            InteractionHand hand,
            NpcTimingBand timing,
            Optional<NpcSoundProfile> sound
    ) implements NpcRoutineStep {
        public UseItem {
            Objects.requireNonNull(hand, "hand");
            Objects.requireNonNull(timing, "timing");
            sound = Objects.requireNonNull(sound, "sound");
        }
    }

    record Sound(NpcSoundProfile sound) implements NpcRoutineStep {
        public Sound {
            Objects.requireNonNull(sound, "sound");
        }
    }

    record Wait(NpcTimingBand timing) implements NpcRoutineStep {
        public Wait {
            Objects.requireNonNull(timing, "timing");
        }
    }
}
