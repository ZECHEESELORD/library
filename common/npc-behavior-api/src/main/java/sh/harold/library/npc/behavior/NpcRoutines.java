package sh.harold.library.npc.behavior;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import sh.harold.library.entity.EquipmentSlot;
import sh.harold.library.entity.InteractionHand;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.spatial.AnchorRef;
import sh.harold.library.spatial.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class NpcRoutines {
    public static final Key LECTERN_STUDY = key("lectern_study");
    public static final Key ANVIL_FORGING = key("anvil_forging");
    public static final Key SHELF_DISTRACTION = key("shelf_distraction");
    public static final Key TABLE_CRAFTING = key("table_crafting");

    private static final ItemDescriptor BOOK = item("book");
    private static final ItemDescriptor FEATHER = item("feather");
    private static final NpcSoundProfile PAGE_CUE = sound("item.book.page_turn", Sound.Source.BLOCK, 0.65f, 0.94f, 1.08f);
    private static final NpcSoundProfile WRITING_CUE = sound("item.book.put", Sound.Source.BLOCK, 0.45f, 1.15f, 1.35f);
    private static final NpcSoundProfile ANVIL_CUE = sound("block.anvil.hit", Sound.Source.BLOCK, 0.8f, 0.9f, 1.08f);
    private static final NpcSoundProfile CRAFT_CUE = sound("ui.button.click", Sound.Source.BLOCK, 0.45f, 0.92f, 1.15f);

    private NpcRoutines() {
    }

    public static NpcRoutine lecternStudy(AnchorRef lectern) {
        Objects.requireNonNull(lectern, "lectern");
        AnchorRef leftPage = offset(lectern, -0.24, 0.08, 0.0);
        AnchorRef rightPage = offset(lectern, 0.24, 0.08, 0.0);
        return NpcRoutine.builder(LECTERN_STUDY)
                .equip(EquipmentSlot.OFF_HAND, BOOK)
                .lookAt(lectern, NpcTimingBand.SHORT)
                .sweep(leftPage, rightPage, NpcTimingBand.LONG)
                .sound(PAGE_CUE)
                .equip(EquipmentSlot.MAIN_HAND, FEATHER)
                .swing(InteractionHand.MAIN_HAND, WRITING_CUE)
                .wait(NpcTimingBand.MEDIUM)
                .sweep(rightPage, leftPage, NpcTimingBand.MEDIUM)
                .build();
    }

    public static NpcRoutine anvilForging(AnchorRef anvil, ItemDescriptor authoredTool) {
        Objects.requireNonNull(anvil, "anvil");
        Objects.requireNonNull(authoredTool, "authoredTool");
        AnchorRef handInspection = offset(anvil, 0.0, 0.85, -0.2);
        return NpcRoutine.builder(ANVIL_FORGING)
                .equip(EquipmentSlot.MAIN_HAND, authoredTool)
                .lookAt(anvil, NpcTimingBand.SHORT)
                .swing(InteractionHand.MAIN_HAND, ANVIL_CUE)
                .wait(NpcTimingBand.QUICK)
                .swing(InteractionHand.MAIN_HAND, ANVIL_CUE)
                .wait(NpcTimingBand.QUICK)
                .swing(InteractionHand.MAIN_HAND, ANVIL_CUE)
                .lookAt(handInspection, NpcTimingBand.MEDIUM)
                .gesture(NpcGesturePreset.HEAD_FLICK_UP)
                .build();
    }

    public static NpcRoutine shelfDistraction(AnchorRef shelf) {
        Objects.requireNonNull(shelf, "shelf");
        AnchorRef shelfTop = offset(shelf, 0.0, 0.9, 0.0);
        AnchorRef left = offset(shelf, -0.65, 0.35, 0.0);
        AnchorRef right = offset(shelf, 0.65, 0.35, 0.0);
        return NpcRoutine.builder(SHELF_DISTRACTION)
                .lookAt(shelfTop, NpcTimingBand.MEDIUM)
                .stance(NpcStance.CROUCHING)
                .sweep(left, right, NpcTimingBand.LONG)
                .gesture(NpcGesturePreset.LOOK_AROUND)
                .stance(NpcStance.STANDING)
                .lookAt(shelf, NpcTimingBand.SHORT)
                .build();
    }

    public static NpcRoutine tableCrafting(AnchorRef craftingTable, Collection<ItemDescriptor> authoredItems) {
        Objects.requireNonNull(craftingTable, "craftingTable");
        List<ItemDescriptor> items = List.copyOf(Objects.requireNonNull(authoredItems, "authoredItems"));
        if (items.isEmpty()) {
            throw new IllegalArgumentException("authoredItems cannot be empty");
        }
        AnchorRef left = offset(craftingTable, -0.25, 0.08, 0.0);
        AnchorRef right = offset(craftingTable, 0.25, 0.08, 0.0);
        return NpcRoutine.builder(TABLE_CRAFTING)
                .equipOneOf(EquipmentSlot.MAIN_HAND, items)
                .lookAt(craftingTable, NpcTimingBand.SHORT)
                .useItem(InteractionHand.MAIN_HAND, NpcTimingBand.SHORT, CRAFT_CUE)
                .sweep(left, right, NpcTimingBand.SHORT)
                .swing(InteractionHand.OFF_HAND, CRAFT_CUE)
                .equipOneOf(EquipmentSlot.MAIN_HAND, items)
                .useItem(InteractionHand.MAIN_HAND, NpcTimingBand.MEDIUM, CRAFT_CUE)
                .build();
    }

    public static NpcRoutine LECTERN_STUDY(AnchorRef lectern) {
        return lecternStudy(lectern);
    }

    public static NpcRoutine ANVIL_FORGING(AnchorRef anvil, ItemDescriptor authoredTool) {
        return anvilForging(anvil, authoredTool);
    }

    public static NpcRoutine SHELF_DISTRACTION(AnchorRef shelf) {
        return shelfDistraction(shelf);
    }

    public static NpcRoutine TABLE_CRAFTING(AnchorRef craftingTable, Collection<ItemDescriptor> authoredItems) {
        return tableCrafting(craftingTable, authoredItems);
    }

    private static AnchorRef offset(AnchorRef anchor, double x, double y, double z) {
        return new AnchorRef.Offset(anchor, new Vec3(x, y, z));
    }

    private static ItemDescriptor item(String value) {
        return new ItemDescriptor(Key.key("minecraft", value), 1);
    }

    private static NpcSoundProfile sound(
            String value,
            Sound.Source source,
            float volume,
            float minimumPitch,
            float maximumPitch
    ) {
        return NpcSoundProfile.builder()
                .sound(Key.key("minecraft", value), source, volume, minimumPitch, maximumPitch)
                .build();
    }

    private static Key key(String value) {
        return Key.key("creative-library", "npc/" + value);
    }
}
