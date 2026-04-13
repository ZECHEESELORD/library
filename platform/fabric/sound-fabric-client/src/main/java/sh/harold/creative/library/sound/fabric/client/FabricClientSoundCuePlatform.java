package sh.harold.creative.library.sound.fabric.client;

import net.kyori.adventure.key.Key;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import sh.harold.creative.library.sound.CuePlayback;
import sh.harold.creative.library.sound.SoundCue;
import sh.harold.creative.library.sound.SoundCueRegistry;
import sh.harold.creative.library.sound.SoundCueService;
import sh.harold.creative.library.sound.SoundTarget;
import sh.harold.creative.library.sound.fabric.FabricSoundSupport;
import sh.harold.creative.library.sound.core.StandardSoundCueService;
import sh.harold.creative.library.spatial.SpaceId;
import sh.harold.creative.library.spatial.Vec3;

import java.util.Objects;

public final class FabricClientSoundCuePlatform implements SoundCueService {

    private final FabricClientSoundCueScheduler scheduler;
    private final SoundCueService sounds;
    private final boolean closeScheduler;

    public FabricClientSoundCuePlatform() {
        this(new FabricClientSoundCueScheduler(), true);
    }

    public FabricClientSoundCuePlatform(SoundCueService sounds) {
        this.scheduler = null;
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.closeScheduler = false;
    }

    private FabricClientSoundCuePlatform(FabricClientSoundCueScheduler scheduler, boolean closeScheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.sounds = new StandardSoundCueService(scheduler);
        this.closeScheduler = closeScheduler;
    }

    public CuePlayback play(LocalPlayer player, SoundCue cue) {
        return play(target(player), cue);
    }

    public CuePlayback play(LocalPlayer player, Key cueKey) {
        return play(target(player), cueKey);
    }

    public CuePlayback play(LocalPlayer player, String cueKey) {
        return play(target(player), cueKey);
    }

    public CuePlayback playToClient(SoundCue cue) {
        return play(target(requirePlayer()), cue);
    }

    public CuePlayback playToClient(Key cueKey) {
        return play(target(requirePlayer()), cueKey);
    }

    public CuePlayback playToClient(String cueKey) {
        return play(target(requirePlayer()), cueKey);
    }

    public CuePlayback playAtClient(Vec3 position, SoundCue cue) {
        return play(target(position), cue);
    }

    public SoundTarget target(LocalPlayer player) {
        LocalPlayer value = Objects.requireNonNull(player, "player");
        return SoundTarget.emitter(sound -> requireLevel().playLocalSound(
                value.getX(),
                value.getY(),
                value.getZ(),
                FabricSoundSupport.toEvent(sound),
                FabricSoundSupport.toSource(sound.source()),
                sound.volume(),
                sound.pitch(),
                false
        ));
    }

    public SoundTarget target(Vec3 position) {
        Vec3 pos = Objects.requireNonNull(position, "position");
        return SoundTarget.positioned(
                SpaceId.of("minecraft", "client"),
                pos,
                (spaceId, soundPosition, sound) -> requireLevel().playLocalSound(
                        soundPosition.x(),
                        soundPosition.y(),
                        soundPosition.z(),
                        FabricSoundSupport.toEvent(sound),
                        FabricSoundSupport.toSource(sound.source()),
                        sound.volume(),
                        sound.pitch(),
                        false
                )
        );
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

    private static LocalPlayer requirePlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            throw new IllegalStateException("Fabric client player is not available");
        }
        return player;
    }

    private static net.minecraft.client.multiplayer.ClientLevel requireLevel() {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            throw new IllegalStateException("Fabric client level is not available");
        }
        return level;
    }
}
