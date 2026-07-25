package sh.harold.library.npc.behavior;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

public final class NpcSoundProfiles {
    public static final NpcSoundProfile SILENT = NpcSoundProfile.silentProfile();
    public static final NpcSoundProfile VILLAGER = NpcSoundProfile.builder()
            .sound(Key.key("minecraft", "entity.villager.ambient"), Sound.Source.NEUTRAL, 0.9f, 0.92f, 1.08f)
            .sound(Key.key("minecraft", "entity.villager.yes"), Sound.Source.NEUTRAL, 0.8f, 0.94f, 1.06f)
            .build();
    public static final NpcSoundProfile FROG = NpcSoundProfile.builder()
            .sound(Key.key("minecraft", "entity.frog.ambient"), Sound.Source.NEUTRAL, 0.85f, 0.92f, 1.08f)
            .build();
    public static final NpcSoundProfile ILLAGER = NpcSoundProfile.builder()
            .sound(Key.key("minecraft", "entity.vindicator.ambient"), Sound.Source.HOSTILE, 0.85f, 0.88f, 1.02f)
            .sound(Key.key("minecraft", "entity.evoker.ambient"), Sound.Source.HOSTILE, 0.8f, 0.9f, 1.04f)
            .build();

    private NpcSoundProfiles() {
    }
}
