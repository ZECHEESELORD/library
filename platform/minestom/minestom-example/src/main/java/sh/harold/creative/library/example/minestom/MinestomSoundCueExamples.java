package sh.harold.creative.library.example.minestom;

import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.timer.TaskSchedule;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.sound.SoundCueKeys;
import sh.harold.creative.library.sound.minestom.MinestomSoundCuePlatform;

final class MinestomSoundCueExamples {

    private static final long ALL_CUE_INTERVAL_TICKS = 12L;

    private final MinestomSoundCuePlatform sounds;
    private final MinestomDevHarnessMessages feedback;

    MinestomSoundCueExamples(MinestomSoundCuePlatform sounds, MinestomDevHarnessMessages feedback) {
        this.sounds = sounds;
        this.feedback = feedback;
    }

    void playAll(Player player) {
        feedback.info(
                player,
                "Playing sound cues in order: {order}.",
                Message.slot("order", "menu, scroll, npc, confirm, deny, levelup, discovery")
        );
        CueExample[] values = CueExample.values();
        for (int index = 0; index < values.length; index++) {
            CueExample cue = values[index];
            MinecraftServer.getSchedulerManager().buildTask(() -> play(player, cue))
                    .delay(TaskSchedule.tick(Math.toIntExact(index * ALL_CUE_INTERVAL_TICKS)))
                    .schedule();
        }
    }

    void playVariant(Player player, String variant) {
        CueExample cue = CueExample.fromVariant(variant);
        if (cue == null) {
            feedback.error(player, "Unknown sound cue variant {variant}.", Message.slot("variant", variant));
            return;
        }
        play(player, cue);
        feedback.success(player, "Played sound cue {cue}.", Message.slot("cue", cue.label));
    }

    private void play(Player player, CueExample cue) {
        sounds.play(player, cue.key);
    }

    private enum CueExample {
        MENU("menu", SoundCueKeys.MENU_CLICK, "menu/click"),
        SCROLL("scroll", SoundCueKeys.MENU_SCROLL, "menu/scroll"),
        NPC("npc", SoundCueKeys.INTERACTION_NPC, "interaction/npc"),
        CONFIRM("confirm", SoundCueKeys.RESULT_CONFIRM, "result/confirm"),
        DENY("deny", SoundCueKeys.RESULT_DENY, "result/deny"),
        LEVELUP("levelup", SoundCueKeys.REWARD_LEVEL_UP, "reward/level_up"),
        DISCOVERY("discovery", SoundCueKeys.REWARD_DISCOVERY, "reward/discovery");

        private final String variant;
        private final Key key;
        private final String label;

        CueExample(String variant, Key key, String label) {
            this.variant = variant;
            this.key = key;
            this.label = label;
        }

        private static CueExample fromVariant(String variant) {
            for (CueExample cue : values()) {
                if (cue.variant.equals(variant)) {
                    return cue;
                }
            }
            return null;
        }
    }
}
