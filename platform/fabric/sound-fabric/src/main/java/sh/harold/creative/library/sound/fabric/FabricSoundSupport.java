package sh.harold.creative.library.sound.fabric;

import net.kyori.adventure.sound.Sound;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.Objects;

public final class FabricSoundSupport {

    private FabricSoundSupport() {
    }

    public static SoundEvent toEvent(Sound sound) {
        return SoundEvent.createVariableRangeEvent(Identifier.parse(Objects.requireNonNull(sound, "sound").name().asString()));
    }

    public static SoundSource toSource(Sound.Source source) {
        return switch (Objects.requireNonNull(source, "source")) {
            case MASTER -> SoundSource.MASTER;
            case MUSIC -> SoundSource.MUSIC;
            case RECORD -> SoundSource.RECORDS;
            case WEATHER -> SoundSource.WEATHER;
            case BLOCK -> SoundSource.BLOCKS;
            case HOSTILE -> SoundSource.HOSTILE;
            case NEUTRAL -> SoundSource.NEUTRAL;
            case PLAYER -> SoundSource.PLAYERS;
            case AMBIENT -> SoundSource.AMBIENT;
            case VOICE -> SoundSource.VOICE;
        };
    }
}
