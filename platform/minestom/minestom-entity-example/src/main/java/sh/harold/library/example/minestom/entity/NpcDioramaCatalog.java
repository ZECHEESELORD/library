package sh.harold.library.example.minestom.entity;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import sh.harold.library.entity.EquipmentSlot;
import sh.harold.library.entity.InteractionHand;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.npc.behavior.NpcAcknowledgementSpec;
import sh.harold.library.npc.behavior.NpcAttentionResponse;
import sh.harold.library.npc.behavior.NpcAttentionSpec;
import sh.harold.library.npc.behavior.NpcBehaviorProfile;
import sh.harold.library.npc.behavior.NpcCooldownRange;
import sh.harold.library.npc.behavior.NpcGesturePreset;
import sh.harold.library.npc.behavior.NpcPersonalityPreset;
import sh.harold.library.npc.behavior.NpcPersonalityTuning;
import sh.harold.library.npc.behavior.NpcRoutine;
import sh.harold.library.npc.behavior.NpcRoutines;
import sh.harold.library.npc.behavior.NpcSoundProfile;
import sh.harold.library.npc.behavior.NpcSoundProfiles;
import sh.harold.library.npc.behavior.NpcStance;
import sh.harold.library.npc.behavior.NpcSustainMode;
import sh.harold.library.npc.behavior.NpcTimingBand;
import sh.harold.library.npc.behavior.NpcVoiceDeliveryStyle;
import sh.harold.library.npc.behavior.NpcVoiceProfile;
import sh.harold.library.npc.behavior.NpcVoiceProfiles;
import sh.harold.library.spatial.AnchorRef;
import sh.harold.library.spatial.AnchorSnapshot;
import sh.harold.library.spatial.Frame3;
import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure authoring catalog shared by the live scene and deterministic tests. */
final class NpcDioramaCatalog {

    static final String LIBRARIAN = "librarian";
    static final String CATALOGUER = "cataloguer";
    static final String SHELVER = "shelver";
    static final String SMITH = "smith";
    static final String APPRENTICE = "apprentice";
    static final String SENTINEL = "sentinel";
    static final String SLEEPY_GUARD = "sleepy_guard";
    static final String COURIER = "courier";

    static final String LECTERN = "lectern";
    static final String LECTERN_REVIEW = "lectern_review";
    static final String CATALOGUING = "cataloguing";
    static final String SHELF = "shelf";
    static final String ANVIL = "anvil";
    static final String CRAFTING = "crafting";
    static final String SMITH_INSPECTION = "smith_inspection";
    static final String GUARD_SCAN = "guard_scan";
    static final String SLEEPY_WATCH = "sleepy_watch";
    static final String ROUTE_CHECK = "route_check";

    private static final NpcSoundProfile LIBRARY_FOLEY = NpcSoundProfile.builder()
            .sound(key("item.book.page_turn"), Sound.Source.BLOCK, 0.55f, 0.92f, 1.08f)
            .sound(key("item.book.put"), Sound.Source.BLOCK, 0.45f, 1.05f, 1.25f)
            .build();
    private static final NpcSoundProfile DESK_FOLEY = NpcSoundProfile.builder()
            .sound(key("ui.button.click"), Sound.Source.BLOCK, 0.4f, 0.9f, 1.1f)
            .sound(key("block.wood.place"), Sound.Source.BLOCK, 0.35f, 1.1f, 1.3f)
            .build();
    private static final NpcSoundProfile WATCH_BELL = NpcSoundProfile.builder()
            .sound(key("block.bell.use"), Sound.Source.BLOCK, 0.55f, 0.9f, 1.0f)
            .build();
    private static final NpcVoiceProfile SOFT_SCRIBE = new NpcVoiceProfile(
            NpcSoundProfile.builder()
                    .sound(key("entity.villager.ambient"), Sound.Source.NEUTRAL, 0.65f, 1.04f, 1.16f)
                    .sound(key("entity.villager.yes"), Sound.Source.NEUTRAL, 0.55f, 1.08f, 1.2f)
                    .build(),
            NpcVoiceDeliveryStyle.SOFT
    );

    private NpcDioramaCatalog() {
    }

    static Catalog create(SpaceId space) {
        Objects.requireNonNull(space, "space");
        Map<String, NpcRoutine> routines = createRoutines(space);
        return new Catalog(routines, createProfiles(routines));
    }

    private static Map<String, NpcRoutine> createRoutines(SpaceId space) {
        AnchorRef lectern = anchor(space, 0.5, 43.1, 12.5);
        AnchorRef catalogueDesk = anchor(space, -4.5, 43.0, 11.5);
        AnchorRef libraryShelf = anchor(space, 4.5, 43.4, 17.4);
        AnchorRef anvil = anchor(space, 15.5, 42.9, 12.5);
        AnchorRef forgeTable = anchor(space, 20.5, 42.9, 11.5);
        AnchorRef forgeFire = anchor(space, 23.5, 43.2, 14.5);
        AnchorRef nearWatch = anchor(space, -16.5, 44.0, 7.5);
        AnchorRef farWatch = anchor(space, -16.5, 45.0, -3.0);
        AnchorRef westRoad = anchor(space, -25.0, 43.0, 2.0);
        AnchorRef eastRoad = anchor(space, -8.0, 43.0, 2.0);
        AnchorRef routeTable = anchor(space, -19.5, 43.0, 9.5);

        Map<String, NpcRoutine> routines = new LinkedHashMap<>();
        routines.put(LECTERN, NpcRoutines.lecternStudy(lectern));
        routines.put(LECTERN_REVIEW, NpcRoutine.builder(exampleKey("lectern_review"))
                .equip(EquipmentSlot.MAIN_HAND, item("writable_book"))
                .lookAt(lectern, NpcTimingBand.SHORT)
                .useItem(InteractionHand.MAIN_HAND, NpcTimingBand.MEDIUM, LIBRARY_FOLEY)
                .gesture(NpcGesturePreset.LEAN_FORWARD_PROXY, LIBRARY_FOLEY)
                .clear(EquipmentSlot.MAIN_HAND)
                .wait(NpcTimingBand.SHORT)
                .build());
        routines.put(SHELF, NpcRoutines.shelfDistraction(libraryShelf));
        routines.put(ANVIL, NpcRoutines.anvilForging(anvil, item("iron_axe")));
        routines.put(CRAFTING, NpcRoutines.tableCrafting(
                forgeTable,
                List.of(item("iron_ingot"), item("copper_ingot"), item("stick"))
        ));
        routines.put(CATALOGUING, NpcRoutine.builder(exampleKey("cataloguing"))
                .equipOneOf(EquipmentSlot.OFF_HAND, List.of(item("paper"), item("book"), item("writable_book")))
                .lookAt(catalogueDesk, NpcTimingBand.SHORT)
                .useItem(InteractionHand.OFF_HAND, NpcTimingBand.SHORT, DESK_FOLEY)
                .equip(EquipmentSlot.MAIN_HAND, item("feather"))
                .swing(InteractionHand.MAIN_HAND, LIBRARY_FOLEY)
                .gesture(NpcGesturePreset.NOD, LIBRARY_FOLEY)
                .clear(EquipmentSlot.MAIN_HAND)
                .wait(NpcTimingBand.QUICK)
                .clear(EquipmentSlot.OFF_HAND)
                .build());
        routines.put(SMITH_INSPECTION, NpcRoutine.builder(exampleKey("smith_inspection"))
                .equip(EquipmentSlot.OFF_HAND, item("iron_ingot"))
                .lookAt(forgeFire, NpcTimingBand.MEDIUM)
                .gesture(NpcGesturePreset.LEAN_FORWARD_PROXY, DESK_FOLEY)
                .wait(NpcTimingBand.SHORT)
                .clear(EquipmentSlot.OFF_HAND)
                .build());
        routines.put(GUARD_SCAN, NpcRoutine.builder(exampleKey("guard_scan"))
                .equip(EquipmentSlot.MAIN_HAND, item("spyglass"))
                .lookAt(nearWatch, NpcTimingBand.MEDIUM)
                .useItem(InteractionHand.MAIN_HAND, NpcTimingBand.LONG)
                .sweep(westRoad, eastRoad, NpcTimingBand.LONG)
                .sound(WATCH_BELL)
                .clear(EquipmentSlot.MAIN_HAND)
                .gesture(NpcGesturePreset.HEAD_FLICK_UP, WATCH_BELL)
                .wait(NpcTimingBand.MEDIUM)
                .build());
        routines.put(SLEEPY_WATCH, NpcRoutine.builder(exampleKey("sleepy_watch"))
                .equip(EquipmentSlot.OFF_HAND, item("lantern"))
                .lookAt(farWatch, NpcTimingBand.LONG)
                .gesture(NpcGesturePreset.LEAN_BACK_PROXY)
                .stance(NpcStance.CROUCHING)
                .wait(NpcTimingBand.LONG)
                .gesture(NpcGesturePreset.CROUCH_PULSE)
                .stance(NpcStance.STANDING)
                .clear(EquipmentSlot.OFF_HAND)
                .build());
        routines.put(ROUTE_CHECK, NpcRoutine.builder(exampleKey("route_check"))
                .equip(EquipmentSlot.OFF_HAND, item("map"))
                .lookAt(routeTable, NpcTimingBand.SHORT)
                .sweep(westRoad, eastRoad, NpcTimingBand.LONG)
                .gesture(NpcGesturePreset.DOUBLE_TAKE, DESK_FOLEY)
                .stance(NpcStance.CROUCHING)
                .lookAt(routeTable, NpcTimingBand.MEDIUM)
                .stance(NpcStance.STANDING)
                .clear(EquipmentSlot.OFF_HAND)
                .build());
        return Map.copyOf(routines);
    }

    private static Map<String, NpcBehaviorProfile> createProfiles(Map<String, NpcRoutine> routines) {
        NpcAttentionSpec welcomingAttention = NpcAttentionSpec.builder()
                .enterRadius(7.0)
                .exitRadius(9.0)
                .idleResponse(NpcAttentionResponse.sustain(
                        NpcSustainMode.NATURAL,
                        NpcAcknowledgementSpec.of(
                                List.of(NpcGesturePreset.WAVE, NpcGesturePreset.NOD),
                                List.of(
                                        text("Welcome to the library.", NamedTextColor.GOLD),
                                        text("Mind the book carts, please.", NamedTextColor.YELLOW)
                                )
                        )
                ))
                .routineResponse(NpcAttentionResponse.acknowledge(NpcAcknowledgementSpec.of(
                        List.of(NpcGesturePreset.NOD, NpcGesturePreset.HEAD_FLICK_UP),
                        List.of(text("I will be with you in a moment.", NamedTextColor.GRAY))
                )))
                .conversationResponse(NpcAttentionResponse.ignore())
                .build();
        NpcAttentionSpec forgeAttention = NpcAttentionSpec.builder()
                .idleResponse(NpcAttentionResponse.sustain(
                        NpcSustainMode.STEADY,
                        NpcAcknowledgementSpec.gestures(NpcGesturePreset.HEAD_FLICK_UP)
                ))
                .routineResponse(NpcAttentionResponse.acknowledge(NpcAcknowledgementSpec.of(
                        List.of(NpcGesturePreset.HEAD_FLICK_UP, NpcGesturePreset.LEAN_BACK_PROXY),
                        List.of(text("Mind the sparks.", NamedTextColor.RED))
                )))
                .build();

        Map<String, NpcBehaviorProfile> profiles = new LinkedHashMap<>();
        profiles.put(LIBRARIAN, NpcBehaviorProfile.builder(NpcPersonalityPreset.WARM)
                .tuning(new NpcPersonalityTuning(1.15, 1.05, 0.9))
                .attention(welcomingAttention)
                .voice(NpcVoiceProfiles.WARM_VILLAGER)
                .idle(routines.get(LECTERN), 6, NpcCooldownRange.seconds(3.0, 7.0))
                .idle(routines.get(LECTERN_REVIEW), 1, NpcCooldownRange.seconds(14.0, 22.0))
                .interactionLine(text("Oh, hello! Looking for a particular volume?", NamedTextColor.GOLD))
                .interactionLine(text("Welcome. I can help you navigate the stacks.", NamedTextColor.YELLOW))
                .propCompletionLine(text("There. Another page in order.", NamedTextColor.GRAY))
                .conversationInterruptionLine(text("One moment - the stacks have a visitor.", NamedTextColor.GRAY))
                .build());
        profiles.put(CATALOGUER, NpcBehaviorProfile.builder(NpcPersonalityPreset.CURIOUS)
                .tuning(new NpcPersonalityTuning(1.0, 1.12, 1.25))
                .voice(SOFT_SCRIBE)
                .idle(routines.get(CATALOGUING), 5, NpcCooldownRange.seconds(5.0, 10.0))
                .idle(routines.get(SHELF), 2, NpcCooldownRange.seconds(10.0, 18.0))
                .interactionLine(text("Is that an uncommon edition?", NamedTextColor.AQUA))
                .propCompletionLine(text("Filed under local curiosities.", NamedTextColor.GRAY))
                .conversationInterruptionLine(text("Oh! We have a reader.", NamedTextColor.GRAY))
                .build());
        profiles.put(SHELVER, NpcBehaviorProfile.builder(NpcPersonalityPreset.DISTRACTED)
                .tuning(new NpcPersonalityTuning(0.95, 0.9, 1.1))
                .voice(NpcVoiceProfiles.SILENT)
                .idle(routines.get(SHELF), 5, NpcCooldownRange.seconds(5.0, 11.0))
                .interactionLine(text("Sorry - which shelf was I on?", NamedTextColor.LIGHT_PURPLE))
                .propCompletionLine(text("Alphabetical. Mostly.", NamedTextColor.GRAY))
                .build());
        profiles.put(SMITH, NpcBehaviorProfile.builder(NpcPersonalityPreset.CONFIDENT)
                .tuning(new NpcPersonalityTuning(1.0, 0.92, 1.2))
                .attention(forgeAttention)
                .voice(NpcVoiceProfiles.DEEP_VILLAGER)
                .idle(routines.get(ANVIL), 6, NpcCooldownRange.seconds(4.0, 8.0))
                .idle(routines.get(SMITH_INSPECTION), 2, NpcCooldownRange.seconds(8.0, 14.0))
                .interactionLine(text("The edge will hold. I guarantee it.", NamedTextColor.GOLD))
                .propCompletionLine(text("Good temper. Clean ring.", NamedTextColor.GRAY))
                .conversationInterruptionLine(text("Hold that thought. Customer.", NamedTextColor.DARK_GRAY))
                .build());
        profiles.put(APPRENTICE, NpcBehaviorProfile.builder(NpcPersonalityPreset.NERVOUS)
                .tuning(new NpcPersonalityTuning(1.05, 1.35, 1.3))
                .voice(new NpcVoiceProfile(NpcSoundProfiles.FROG, NpcVoiceDeliveryStyle.HESITANT))
                .idle(routines.get(CRAFTING), 5, NpcCooldownRange.seconds(4.0, 9.0))
                .idle(routines.get(SMITH_INSPECTION), 1, NpcCooldownRange.seconds(12.0, 20.0))
                .interactionLine(text("I did measure twice. I think.", NamedTextColor.GREEN))
                .propCompletionLine(text("That was meant to click like that.", NamedTextColor.GRAY))
                .build());
        profiles.put(SENTINEL, NpcBehaviorProfile.builder(NpcPersonalityPreset.NEUTRAL)
                .attention(NpcAttentionSpec.builder()
                        .idleResponse(NpcAttentionResponse.sustain(NpcSustainMode.STEADY))
                        .routineResponse(NpcAttentionResponse.acknowledge(
                                NpcAcknowledgementSpec.gestures(NpcGesturePreset.NOD)
                        ))
                        .build())
                .voice(NpcVoiceProfiles.HARSH_ILLAGER)
                .idle(routines.get(GUARD_SCAN), 4, NpcCooldownRange.seconds(7.0, 13.0))
                .interactionLine(text("Road is clear.", NamedTextColor.GRAY))
                .propCompletionLine(text("Nothing unusual on the western road.", NamedTextColor.DARK_GRAY))
                .build());
        profiles.put(SLEEPY_GUARD, NpcBehaviorProfile.builder(NpcPersonalityPreset.SLEEPY)
                .tuning(new NpcPersonalityTuning(0.85, 0.62, 0.65))
                .voice(new NpcVoiceProfile(NpcSoundProfiles.VILLAGER, NpcVoiceDeliveryStyle.SLEEPY))
                .idle(routines.get(SLEEPY_WATCH), 5, NpcCooldownRange.seconds(5.0, 10.0))
                .interactionLine(text("Mm? My watch is not over yet.", NamedTextColor.BLUE))
                .propCompletionLine(text("Still awake.", NamedTextColor.DARK_GRAY))
                .conversationInterruptionLine(text("I was listening. Mostly.", NamedTextColor.GRAY))
                .build());
        profiles.put(COURIER, NpcBehaviorProfile.builder(NpcPersonalityPreset.CONFUSED)
                .tuning(new NpcPersonalityTuning(1.1, 1.0, 1.45))
                .voice(new NpcVoiceProfile(NpcSoundProfiles.VILLAGER, NpcVoiceDeliveryStyle.HESITANT))
                .idle(routines.get(ROUTE_CHECK), 5, NpcCooldownRange.seconds(4.0, 9.0))
                .idle(routines.get(GUARD_SCAN), 1, NpcCooldownRange.seconds(16.0, 24.0))
                .interactionLine(text("The library was east. Or was that the forge?", NamedTextColor.YELLOW))
                .propCompletionLine(text("No, this map is definitely upside down.", NamedTextColor.GRAY))
                .build());
        return Map.copyOf(profiles);
    }

    private static AnchorRef anchor(SpaceId space, double x, double y, double z) {
        return new AnchorRef.Fixed(new AnchorSnapshot(space, Frame3.world(new Vec3(x, y, z))));
    }

    private static ItemDescriptor item(String value) {
        return new ItemDescriptor(key(value), 1);
    }

    private static Key key(String value) {
        return Key.key("minecraft", value);
    }

    private static Key exampleKey(String value) {
        return Key.key("creative-library-example", "npc/" + value);
    }

    private static Component text(String value, NamedTextColor color) {
        return Component.text(value, color);
    }

    record Catalog(Map<String, NpcRoutine> routines, Map<String, NpcBehaviorProfile> profiles) {
        Catalog {
            routines = Map.copyOf(routines);
            profiles = Map.copyOf(profiles);
        }

        NpcRoutine routine(String name) {
            NpcRoutine routine = routines.get(name);
            if (routine == null) {
                throw new IllegalArgumentException("Unknown NPC routine: " + name);
            }
            return routine;
        }

        NpcBehaviorProfile profile(String name) {
            NpcBehaviorProfile profile = profiles.get(name);
            if (profile == null) {
                throw new IllegalArgumentException("Unknown NPC profile: " + name);
            }
            return profile;
        }
    }
}
