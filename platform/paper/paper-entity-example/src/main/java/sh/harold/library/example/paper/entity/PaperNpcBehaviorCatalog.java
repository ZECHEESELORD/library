package sh.harold.library.example.paper.entity;

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
import sh.harold.library.npc.behavior.NpcConversationTopic;
import sh.harold.library.npc.behavior.NpcCooldownRange;
import sh.harold.library.npc.behavior.NpcGesturePreset;
import sh.harold.library.npc.behavior.NpcPersonalityPreset;
import sh.harold.library.npc.behavior.NpcPersonalityTuning;
import sh.harold.library.npc.behavior.NpcRoutine;
import sh.harold.library.npc.behavior.NpcRoutines;
import sh.harold.library.npc.behavior.NpcSoundProfile;
import sh.harold.library.npc.behavior.NpcStance;
import sh.harold.library.npc.behavior.NpcSustainMode;
import sh.harold.library.npc.behavior.NpcTimingBand;
import sh.harold.library.npc.behavior.NpcVoiceDeliveryStyle;
import sh.harold.library.npc.behavior.NpcVoiceProfile;
import sh.harold.library.npc.behavior.NpcVoiceProfiles;
import sh.harold.library.spatial.AnchorRef;
import sh.harold.library.spatial.Vec3;

import java.util.List;

/**
 * Pure, platform-independent authoring for the Paper example's two scenes.
 * Keeping profiles, routines, and topics here makes the example useful as a
 * copyable API catalogue and lets tests inspect it without booting a server.
 */
final class PaperNpcBehaviorCatalog {

    private static final NpcSoundProfile LIBRARY_PROP_CUES = NpcSoundProfile.builder()
            .sound(Key.key("minecraft", "item.book.page_turn"), Sound.Source.BLOCK, 0.55f, 0.94f, 1.08f)
            .sound(Key.key("minecraft", "item.book.put"), Sound.Source.BLOCK, 0.45f, 1.08f, 1.24f)
            .build();
    private static final NpcSoundProfile FORGE_INSPECTION_CUES = NpcSoundProfile.builder()
            .sound(Key.key("minecraft", "block.anvil.use"), Sound.Source.BLOCK, 0.55f, 0.92f, 1.04f)
            .sound(Key.key("minecraft", "item.armor.equip_iron"), Sound.Source.BLOCK, 0.45f, 0.96f, 1.08f)
            .build();
    private static final NpcVoiceProfile HESITANT_VILLAGER = new NpcVoiceProfile(
            NpcSoundProfile.builder()
                    .sound(Key.key("minecraft", "entity.villager.ambient"), Sound.Source.NEUTRAL, 0.7f, 1.03f, 1.15f)
                    .sound(Key.key("minecraft", "entity.villager.yes"), Sound.Source.NEUTRAL, 0.65f, 1.08f, 1.2f)
                    .build(),
            NpcVoiceDeliveryStyle.HESITANT
    );
    private static final NpcVoiceProfile SLEEPY_VILLAGER = new NpcVoiceProfile(
            NpcSoundProfile.builder()
                    .sound(Key.key("minecraft", "entity.villager.ambient"), Sound.Source.NEUTRAL, 0.55f, 0.76f, 0.88f)
                    .build(),
            NpcVoiceDeliveryStyle.SLEEPY
    );

    private PaperNpcBehaviorCatalog() {
    }

    static AuthoredBehaviors author(Anchors anchors) {
        NpcRoutine librarianStudy = NpcRoutines.lecternStudy(anchors.librarianLectern());
        NpcRoutine reshelveNotes = reshelveNotes(anchors.archiveShelf());
        NpcRoutine archiveSearch = NpcRoutines.shelfDistraction(anchors.archiveShelf());
        NpcRoutine scribeWork = NpcRoutines.tableCrafting(
                anchors.scribeTable(),
                List.of(item("paper"), item("writable_book"), item("ink_sac"))
        );
        NpcRoutine researchSearch = NpcRoutines.shelfDistraction(anchors.researchShelf());
        NpcRoutine sleepyReading = NpcRoutines.lecternStudy(anchors.nightLectern());
        NpcRoutine forgeWork = NpcRoutines.anvilForging(anchors.forgeAnvil(), item("iron_pickaxe"));
        NpcRoutine inspectTools = inspectTools(anchors.forgeAnvil());
        NpcRoutine apprenticeWork = NpcRoutines.tableCrafting(
                anchors.apprenticeTable(),
                List.of(item("iron_ingot"), item("stick"), item("coal"))
        );
        NpcRoutine stockCheck = NpcRoutines.tableCrafting(
                anchors.quartermasterTable(),
                List.of(item("iron_ingot"), item("coal"), item("oak_planks"))
        );

        return new AuthoredBehaviors(
                librarianProfile(librarianStudy, reshelveNotes),
                archivistProfile(archiveSearch, reshelveNotes),
                scribeProfile(scribeWork),
                researcherProfile(researchSearch),
                nightClerkProfile(sleepyReading),
                blacksmithProfile(forgeWork, inspectTools),
                apprenticeProfile(apprenticeWork, inspectTools),
                quartermasterProfile(stockCheck),
                librarianStudy,
                forgeWork,
                reshelveNotes,
                inspectTools
        );
    }

    static NpcConversationTopic libraryCatalogueTopic() {
        return NpcConversationTopic.of(
                Key.key("paper-example", "library-catalogue"),
                List.of(
                        line("The west catalogue is finally in order.", NamedTextColor.GRAY),
                        line("Did anyone return the atlas of old roads?", NamedTextColor.GOLD),
                        line("The blue folio belongs on the upper shelf.", NamedTextColor.AQUA),
                        line("Please leave a note when you borrow a reference copy.", NamedTextColor.YELLOW),
                        line("There is more dust here than yesterday.", NamedTextColor.GRAY),
                        line("A reader asked for the oldest map in the collection.", NamedTextColor.GREEN),
                        line("The ink needs another moment to dry.", NamedTextColor.DARK_AQUA),
                        line("Quiet rooms somehow create the longest conversations.", NamedTextColor.LIGHT_PURPLE)
                ),
                List.of(
                        line("Sorry, were you looking for one of us?", NamedTextColor.YELLOW),
                        line("We can finish sorting this in a moment.", NamedTextColor.GRAY)
                )
        );
    }

    static NpcConversationTopic libraryClosingTopic() {
        return NpcConversationTopic.of(
                Key.key("paper-example", "library-closing-rounds"),
                List.of(
                        line("Has the last reading lamp been checked?", NamedTextColor.GRAY),
                        line("One desk still has a stack of notes on it.", NamedTextColor.DARK_AQUA),
                        line("I thought I heard a book fall in the next aisle.", NamedTextColor.LIGHT_PURPLE),
                        line("We should make one more round before closing.", NamedTextColor.YELLOW),
                        line("The quiet section is, for once, actually quiet.", NamedTextColor.GRAY)
                ),
                List.of(line("Oh! We did not hear you come in.", NamedTextColor.YELLOW))
        );
    }

    static NpcConversationTopic forgeOrdersTopic() {
        return NpcConversationTopic.of(
                Key.key("paper-example", "forge-orders"),
                List.of(
                        line("The next hinge wants a little less heat.", NamedTextColor.GOLD),
                        line("Three tool heads are ready for handles.", NamedTextColor.GRAY),
                        line("That last edge came out cleaner than expected.", NamedTextColor.GREEN),
                        line("Coal first, then the smaller fittings.", NamedTextColor.DARK_GRAY),
                        line("The quartermaster counted the ingots twice.", NamedTextColor.WHITE),
                        line("A steady rhythm saves more time than rushing.", NamedTextColor.YELLOW)
                ),
                List.of(line("Mind the hot metal while we make room.", NamedTextColor.RED))
        );
    }

    static ItemDescriptor item(String value) {
        return new ItemDescriptor(Key.key("minecraft", value), 1);
    }

    private static NpcRoutine reshelveNotes(AnchorRef shelf) {
        AnchorRef lowShelf = offset(shelf, 0.0, -0.45, 0.0);
        AnchorRef highShelf = offset(shelf, 0.0, 0.55, 0.0);
        return NpcRoutine.builder(Key.key("paper-example", "routine/reshelve-notes"))
                .equipOneOf(EquipmentSlot.MAIN_HAND, List.of(item("book"), item("writable_book"), item("paper")))
                .lookAt(lowShelf, NpcTimingBand.SHORT)
                .stance(NpcStance.CROUCHING)
                .sweep(lowShelf, highShelf, NpcTimingBand.LONG)
                .gesture(NpcGesturePreset.DOUBLE_TAKE, LIBRARY_PROP_CUES)
                .useItem(InteractionHand.MAIN_HAND, NpcTimingBand.SHORT, LIBRARY_PROP_CUES)
                .clear(EquipmentSlot.MAIN_HAND)
                .stance(NpcStance.STANDING)
                .gesture(NpcGesturePreset.NOD)
                .sound(LIBRARY_PROP_CUES)
                .wait(NpcTimingBand.SHORT)
                .build();
    }

    private static NpcRoutine inspectTools(AnchorRef anvil) {
        AnchorRef nearHand = offset(anvil, 0.0, 0.65, -0.2);
        return NpcRoutine.builder(Key.key("paper-example", "routine/inspect-tools"))
                .equipOneOf(EquipmentSlot.MAIN_HAND, List.of(item("iron_pickaxe"), item("iron_axe")))
                .lookAt(anvil, NpcTimingBand.QUICK)
                .swing(InteractionHand.MAIN_HAND, FORGE_INSPECTION_CUES)
                .lookAt(nearHand, NpcTimingBand.MEDIUM)
                .gesture(NpcGesturePreset.LEAN_FORWARD_PROXY, FORGE_INSPECTION_CUES)
                .clear(EquipmentSlot.MAIN_HAND)
                .wait(NpcTimingBand.SHORT)
                .build();
    }

    private static NpcBehaviorProfile librarianProfile(NpcRoutine study, NpcRoutine reshelve) {
        return NpcBehaviorProfile.builder(NpcPersonalityPreset.WARM)
                .tuning(new NpcPersonalityTuning(1.15, 1.08, 0.9))
                .attention(attention(
                        6.5, 8.5, NpcSustainMode.NATURAL,
                        NpcGesturePreset.WAVE, "Welcome. I will be with you in a moment.",
                        List.of(NpcGesturePreset.NOD, NpcGesturePreset.HEAD_FLICK_UP),
                        "One moment - I am finishing this line."
                ))
                .voice(NpcVoiceProfiles.WARM_VILLAGER)
                .idle(study, 4, NpcCooldownRange.seconds(4.0, 8.0))
                .idle(reshelve, 1, NpcCooldownRange.seconds(12.0, 20.0))
                .interactionLine(line("Oh, hello. Did you need help finding a book?", NamedTextColor.GOLD))
                .interactionLine(line("Welcome back. I can mark my place here.", NamedTextColor.YELLOW))
                .propCompletionLine(line("There. That entry is legible now.", NamedTextColor.GRAY))
                .conversationInterruptionLine(line("Excuse me - a reader needs the desk.", NamedTextColor.YELLOW))
                .build();
    }

    private static NpcBehaviorProfile archivistProfile(NpcRoutine search, NpcRoutine reshelve) {
        return NpcBehaviorProfile.builder(NpcPersonalityPreset.CURIOUS)
                .tuning(new NpcPersonalityTuning(1.1, 1.15, 1.25))
                .attention(attention(
                        6.0, 8.0, NpcSustainMode.NATURAL,
                        NpcGesturePreset.DOUBLE_TAKE, "Hm? Are you looking for something unusual?",
                        List.of(NpcGesturePreset.HEAD_FLICK_UP, NpcGesturePreset.LEAN_FORWARD_PROXY),
                        "Did you spot the missing volume?"
                ))
                .voice(new NpcVoiceProfile(LIBRARY_PROP_CUES, NpcVoiceDeliveryStyle.SOFT))
                .idle(search, 3, NpcCooldownRange.seconds(7.0, 12.0))
                .idle(reshelve, 2, NpcCooldownRange.seconds(10.0, 18.0))
                .interactionLine(line("Tell me which shelf caught your eye.", NamedTextColor.GREEN))
                .propCompletionLine(line("Curious. This was filed under the wrong century.", NamedTextColor.GRAY))
                .conversationInterruptionLine(line("I should probably stop speculating aloud.", NamedTextColor.GRAY))
                .build();
    }

    private static NpcBehaviorProfile scribeProfile(NpcRoutine work) {
        return NpcBehaviorProfile.builder(NpcPersonalityPreset.DISTRACTED)
                .tuning(new NpcPersonalityTuning(0.9, 0.85, 0.8))
                .attention(attention(
                        5.5, 7.5, NpcSustainMode.NATURAL,
                        NpcGesturePreset.HEAD_FLICK_DOWN, "Oh - sorry, I was counting lines.",
                        List.of(NpcGesturePreset.NOD, NpcGesturePreset.HEAD_FLICK_DOWN),
                        "Let me finish this word."
                ))
                .voice(NpcVoiceProfiles.SILENT)
                .idle(work, 4, NpcCooldownRange.seconds(5.0, 10.0))
                .interactionLine(line("Was that question for me?", NamedTextColor.AQUA))
                .propCompletionLine(line("I have lost my place again.", NamedTextColor.GRAY))
                .build();
    }

    private static NpcBehaviorProfile researcherProfile(NpcRoutine search) {
        return NpcBehaviorProfile.builder(NpcPersonalityPreset.CONFUSED)
                .tuning(new NpcPersonalityTuning(1.2, 1.0, 1.4))
                .attention(attention(
                        6.0, 8.5, NpcSustainMode.NATURAL,
                        NpcGesturePreset.LOOK_AROUND, "Did you call my name, or was that from the next aisle?",
                        List.of(NpcGesturePreset.DOUBLE_TAKE, NpcGesturePreset.CROUCH_PULSE),
                        "This is not the amphibian shelf, is it?"
                ))
                .voice(NpcVoiceProfiles.FROG)
                .idle(search, 3, NpcCooldownRange.seconds(6.0, 12.0))
                .interactionLine(line("I seem to have followed the wrong index again.", NamedTextColor.LIGHT_PURPLE))
                .propCompletionLine(line("No, that definitely says cartography.", NamedTextColor.GRAY))
                .conversationInterruptionLine(line("Were we talking about frogs? I hope so.", NamedTextColor.GREEN))
                .build();
    }

    private static NpcBehaviorProfile nightClerkProfile(NpcRoutine reading) {
        return NpcBehaviorProfile.builder(NpcPersonalityPreset.SLEEPY)
                .tuning(new NpcPersonalityTuning(0.8, 0.65, 0.55))
                .attention(attention(
                        5.0, 7.5, NpcSustainMode.STEADY,
                        NpcGesturePreset.HEAD_FLICK_UP, "Mm? The library is still open.",
                        List.of(NpcGesturePreset.HEAD_FLICK_UP),
                        "I am awake. Mostly."
                ))
                .voice(SLEEPY_VILLAGER)
                .idle(reading, 2, NpcCooldownRange.seconds(12.0, 20.0))
                .interactionLine(line("The night catalogue is on this desk somewhere.", NamedTextColor.GRAY))
                .propCompletionLine(line("A short rest for the eyes would help.", NamedTextColor.DARK_GRAY))
                .conversationInterruptionLine(line("Sorry - I missed the last part.", NamedTextColor.GRAY))
                .build();
    }

    private static NpcBehaviorProfile blacksmithProfile(NpcRoutine forge, NpcRoutine inspect) {
        return NpcBehaviorProfile.builder(NpcPersonalityPreset.CONFIDENT)
                .tuning(new NpcPersonalityTuning(1.0, 1.1, 1.15))
                .attention(NpcAttentionSpec.builder()
                        .enterRadius(6.0)
                        .exitRadius(8.0)
                        .idleResponse(NpcAttentionResponse.sustain(
                                NpcSustainMode.STEADY,
                                acknowledgement(NpcGesturePreset.NOD, "Stand clear and I will hear you.")
                        ))
                        .routineResponse(NpcAttentionResponse.ignore())
                        .conversationResponse(NpcAttentionResponse.ignore())
                        .build())
                .voice(NpcVoiceProfiles.DEEP_VILLAGER)
                .idle(forge, 5, NpcCooldownRange.seconds(4.0, 8.0))
                .idle(inspect, 2, NpcCooldownRange.seconds(9.0, 15.0))
                .interactionLine(line("Give me one clean strike, then we can talk.", NamedTextColor.GOLD))
                .propCompletionLine(line("A sound edge. That will hold.", NamedTextColor.GRAY))
                .conversationInterruptionLine(line("Pip, bank the coals. We have company.", NamedTextColor.YELLOW))
                .build();
    }

    private static NpcBehaviorProfile apprenticeProfile(NpcRoutine work, NpcRoutine inspect) {
        return NpcBehaviorProfile.builder(NpcPersonalityPreset.NERVOUS)
                .tuning(new NpcPersonalityTuning(1.25, 1.35, 1.3))
                .attention(attention(
                        6.5, 8.5, NpcSustainMode.NATURAL,
                        NpcGesturePreset.CROUCH_PULSE, "Hello! Please do not stand too close to the sparks.",
                        List.of(NpcGesturePreset.CROUCH_PULSE, NpcGesturePreset.HEAD_FLICK_DOWN),
                        "Was that swing all right?"
                ))
                .voice(HESITANT_VILLAGER)
                .idle(work, 4, NpcCooldownRange.seconds(5.0, 10.0))
                .idle(inspect, 1, NpcCooldownRange.seconds(12.0, 18.0))
                .interactionLine(line("I can fetch the master smith if this is urgent.", NamedTextColor.YELLOW))
                .propCompletionLine(line("Nothing cracked. Good. That is good.", NamedTextColor.GRAY))
                .build();
    }

    private static NpcBehaviorProfile quartermasterProfile(NpcRoutine stockCheck) {
        return NpcBehaviorProfile.builder(NpcPersonalityPreset.NEUTRAL)
                .tuning(NpcPersonalityTuning.DEFAULT)
                .attention(attention(
                        6.0, 8.0, NpcSustainMode.STEADY,
                        NpcGesturePreset.NOD, "State what you need and I will check the ledger.",
                        List.of(NpcGesturePreset.HEAD_FLICK_UP),
                        "One count remaining."
                ))
                .voice(NpcVoiceProfiles.HARSH_ILLAGER)
                .idle(stockCheck, 3, NpcCooldownRange.seconds(8.0, 14.0))
                .interactionLine(line("Orders, repairs, and material requests go in the ledger.", NamedTextColor.WHITE))
                .propCompletionLine(line("Stock count confirmed.", NamedTextColor.GRAY))
                .conversationInterruptionLine(line("The inventory can wait. Briefly.", NamedTextColor.GRAY))
                .build();
    }

    private static NpcAttentionSpec attention(
            double enterRadius,
            double exitRadius,
            NpcSustainMode sustainMode,
            NpcGesturePreset acquisitionGesture,
            String acquisitionBark,
            List<NpcGesturePreset> routineGestures,
            String routineBark
    ) {
        return NpcAttentionSpec.builder()
                .enterRadius(enterRadius)
                .exitRadius(exitRadius)
                .maximumVerticalDifference(4.0)
                .sameSpaceRequired(true)
                .lineOfSightRequired(true)
                .lineOfSightProbeIntervalTicks(4)
                .lineOfSightFailuresBeforeRelease(3)
                .idleResponse(NpcAttentionResponse.sustain(
                        sustainMode,
                        acknowledgement(acquisitionGesture, acquisitionBark)
                ))
                .routineResponse(NpcAttentionResponse.acknowledge(NpcAcknowledgementSpec.of(
                        routineGestures,
                        List.of(line(routineBark, NamedTextColor.YELLOW))
                )))
                .conversationResponse(NpcAttentionResponse.ignore())
                .build();
    }

    private static NpcAcknowledgementSpec acknowledgement(NpcGesturePreset gesture, String bark) {
        return NpcAcknowledgementSpec.of(
                List.of(gesture),
                List.of(line(bark, NamedTextColor.YELLOW))
        );
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color);
    }

    private static AnchorRef offset(AnchorRef base, double x, double y, double z) {
        return new AnchorRef.Offset(base, new Vec3(x, y, z));
    }

    record Anchors(
            AnchorRef librarianLectern,
            AnchorRef archiveShelf,
            AnchorRef scribeTable,
            AnchorRef researchShelf,
            AnchorRef nightLectern,
            AnchorRef forgeAnvil,
            AnchorRef apprenticeTable,
            AnchorRef quartermasterTable
    ) {
    }

    record AuthoredBehaviors(
            NpcBehaviorProfile librarianProfile,
            NpcBehaviorProfile archivistProfile,
            NpcBehaviorProfile scribeProfile,
            NpcBehaviorProfile researcherProfile,
            NpcBehaviorProfile nightClerkProfile,
            NpcBehaviorProfile blacksmithProfile,
            NpcBehaviorProfile apprenticeProfile,
            NpcBehaviorProfile quartermasterProfile,
            NpcRoutine librarianStudy,
            NpcRoutine forgeWork,
            NpcRoutine reshelveNotes,
            NpcRoutine inspectTools
    ) {
    }
}
