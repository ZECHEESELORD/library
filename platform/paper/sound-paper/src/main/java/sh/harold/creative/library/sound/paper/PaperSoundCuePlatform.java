package sh.harold.creative.library.sound.paper;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.creative.library.sound.CuePlayback;
import sh.harold.creative.library.sound.SoundCue;
import sh.harold.creative.library.sound.SoundCueRegistry;
import sh.harold.creative.library.sound.SoundCueService;
import sh.harold.creative.library.sound.core.StandardSoundCueService;

import java.util.Objects;

public final class PaperSoundCuePlatform implements SoundCueService {

    private final SoundCueService sounds;

    public PaperSoundCuePlatform(JavaPlugin plugin) {
        this(new StandardSoundCueService(new PaperSoundCueScheduler(plugin)));
    }

    public PaperSoundCuePlatform(SoundCueService sounds) {
        this.sounds = Objects.requireNonNull(sounds, "sounds");
    }

    public CuePlayback play(CommandSender sender, SoundCue cue) {
        return play(asAudience(sender), cue);
    }

    public CuePlayback play(CommandSender sender, Key cueKey) {
        return play(asAudience(sender), cueKey);
    }

    public CuePlayback play(CommandSender sender, String cueKey) {
        return play(asAudience(sender), cueKey);
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

    private static Audience asAudience(CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        if (sender instanceof Audience audience) {
            return audience;
        }
        throw new IllegalArgumentException("Paper sender does not implement Audience: " + sender.getClass().getName());
    }
}
