package sh.harold.library.npc.behavior.core;

import net.kyori.adventure.text.Component;
import sh.harold.library.npc.behavior.NpcConversationStagingMode;
import sh.harold.library.npc.behavior.NpcPlayback;
import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Internal bridge implemented by configured mannequin behavior actors. */
public interface NpcConversationParticipant {
    UUID actorId();

    boolean configured();

    boolean atCleanupCheckpoint();

    int trackingViewerCount();

    Optional<SpaceId> spaceId();

    Vec3 position();

    boolean tryReserveConversation(UUID registrationId);

    boolean conversationReservedBy(UUID registrationId);

    void conversationInterruption(boolean active);

    void releaseConversation(UUID registrationId);

    NpcPlayback speakConversation(Component line, boolean interruption);

    void clearConversationSpeech();

    List<Component> interruptionLines(List<Component> generic);

    void finishDeferredInteraction(UUID viewerId);

    AutoCloseable beginInterruptionBarrier();

    void stageConversation(NpcConversationStagingMode mode, Vec3 focus, boolean selectedToReact);

    void clearConversationStage();

    void reactToInterruption();

    void interactionRouter(NpcBehaviorActor.InteractionRouter router);
}
