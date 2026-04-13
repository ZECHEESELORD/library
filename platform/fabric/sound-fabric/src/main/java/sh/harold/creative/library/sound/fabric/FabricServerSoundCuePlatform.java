package sh.harold.creative.library.sound.fabric;

import net.kyori.adventure.key.Key;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import sh.harold.creative.library.sound.CuePlayback;
import sh.harold.creative.library.sound.SoundCue;
import sh.harold.creative.library.sound.SoundCueRegistry;
import sh.harold.creative.library.sound.SoundCueService;
import sh.harold.creative.library.sound.SoundTarget;
import sh.harold.creative.library.sound.core.StandardSoundCueService;
import sh.harold.creative.library.spatial.SpaceId;
import sh.harold.creative.library.spatial.Vec3;

import java.util.Objects;

public final class FabricServerSoundCuePlatform implements SoundCueService {

    private final FabricServerSoundCueScheduler scheduler;
    private final SoundCueService sounds;
    private final boolean closeScheduler;

    public FabricServerSoundCuePlatform() {
        this(new FabricServerSoundCueScheduler(), true);
    }

    public FabricServerSoundCuePlatform(SoundCueService sounds) {
        this.scheduler = null;
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.closeScheduler = false;
    }

    private FabricServerSoundCuePlatform(FabricServerSoundCueScheduler scheduler, boolean closeScheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.sounds = new StandardSoundCueService(scheduler);
        this.closeScheduler = closeScheduler;
    }

    public CuePlayback play(ServerPlayer player, SoundCue cue) {
        return play(target(player), cue);
    }

    public CuePlayback play(ServerPlayer player, Key cueKey) {
        return play(target(player), cueKey);
    }

    public CuePlayback play(ServerPlayer player, String cueKey) {
        return play(target(player), cueKey);
    }

    public CuePlayback play(ServerLevel level, Vec3 position, SoundCue cue) {
        return play(target(level, position), cue);
    }

    public CuePlayback play(ServerLevel level, Vec3 position, Key cueKey) {
        return play(target(level, position), cueKey);
    }

    public CuePlayback play(ServerLevel level, Vec3 position, String cueKey) {
        return play(target(level, position), cueKey);
    }

    public SoundTarget target(ServerPlayer player) {
        ServerPlayer value = Objects.requireNonNull(player, "player");
        return SoundTarget.emitter(sound -> value.connection.send(new ClientboundSoundEntityPacket(
                Holder.direct(FabricSoundSupport.toEvent(sound)),
                FabricSoundSupport.toSource(sound.source()),
                value,
                sound.volume(),
                sound.pitch(),
                0L
        )));
    }

    public SoundTarget target(ServerLevel level, Vec3 position) {
        ServerLevel world = Objects.requireNonNull(level, "level");
        Vec3 pos = Objects.requireNonNull(position, "position");
        return SoundTarget.positioned(spaceId(world), pos, (spaceId, soundPosition, sound) -> world.playSound(
                null,
                soundPosition.x(),
                soundPosition.y(),
                soundPosition.z(),
                FabricSoundSupport.toEvent(sound),
                FabricSoundSupport.toSource(sound.source()),
                sound.volume(),
                sound.pitch()
        ));
    }

    @Override
    public SoundCueRegistry registry() {
        return sounds.registry();
    }

    @Override
    public CuePlayback play(SoundTarget target, SoundCue cue) {
        return sounds.play(target, cue);
    }

    @Override
    public void close() {
        sounds.close();
        if (closeScheduler && scheduler != null) {
            scheduler.close();
        }
    }

    private static SpaceId spaceId(ServerLevel level) {
        Identifier location = Objects.requireNonNull(level, "level").dimension().identifier();
        return SpaceId.of(Key.key(location.getNamespace(), location.getPath()));
    }
}
