package sh.harold.creative.library.sound.minestom;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.timer.Scheduler;
import sh.harold.creative.library.sound.CuePlayback;
import sh.harold.creative.library.sound.SoundCue;
import sh.harold.creative.library.sound.SoundCueRegistry;
import sh.harold.creative.library.sound.SoundCueService;
import sh.harold.creative.library.sound.core.StandardSoundCueService;

import java.util.Objects;

public final class MinestomSoundCuePlatform implements SoundCueService {

    private final SoundCueService sounds;

    public MinestomSoundCuePlatform() {
        this(MinecraftServer.getSchedulerManager());
    }

    public MinestomSoundCuePlatform(Scheduler scheduler) {
        this(new StandardSoundCueService(new MinestomSoundCueScheduler(scheduler)));
    }

    public MinestomSoundCuePlatform(SoundCueService sounds) {
        this.sounds = Objects.requireNonNull(sounds, "sounds");
    }

    public CuePlayback play(Player player, SoundCue cue) {
        return play(asAudience(player), cue);
    }

    public CuePlayback play(Player player, Key cueKey) {
        return play(asAudience(player), cueKey);
    }

    public CuePlayback play(Player player, String cueKey) {
        return play(asAudience(player), cueKey);
    }

    @Override
    public SoundCueRegistry registry() {
        return sounds.registry();
    }

    @Override
    public CuePlayback play(Audience audience, SoundCue cue) {
        return sounds.play(audience, cue);
    }

    @Override
    public void close() {
        sounds.close();
    }

    private static Audience asAudience(Player player) {
        Objects.requireNonNull(player, "player");
        if (player instanceof Audience audience) {
            return audience;
        }
        throw new IllegalArgumentException("Minestom player does not implement Audience: " + player.getClass().getName());
    }
}
