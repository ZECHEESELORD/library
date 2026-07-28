package sh.harold.library.entity.minestom;

import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.EntityPose;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.PlayerHand;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.npc.behavior.NpcAttentionResponse;
import sh.harold.library.npc.behavior.NpcAttentionSpec;
import sh.harold.library.npc.behavior.NpcBehaviorProfile;
import sh.harold.library.npc.behavior.NpcSustainMode;
import sh.harold.library.npc.behavior.core.NpcAttentionStack;
import sh.harold.library.npc.behavior.core.NpcBehaviorActor;
import sh.harold.library.npc.behavior.core.NpcBehaviorRenderPort;
import sh.harold.library.npc.behavior.core.NpcRenderFrame;
import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinestomNpcBehaviorRenderPortTest {

    @BeforeAll
    static void initializeMinestom() {
        MinecraftServer.init();
    }

    @AfterAll
    static void stopMinestom() {
        MinecraftServer.stopCleanly();
    }

    @Test
    void sharedFrameUsesNativeMannequinChannelsAndRestoreUsesLatestAuthoredBase() {
        LivingEntity mannequin = new LivingEntity(EntityType.MANNEQUIN);
        MinestomNpcBehaviorRenderPort port = new MinestomNpcBehaviorRenderPort(
                mannequin,
                ignored -> SpaceId.of("test", "instance")
        );
        ItemDescriptor axe = new ItemDescriptor(Key.key("minecraft", "iron_axe"), 1);
        NpcRenderFrame performance = new NpcRenderFrame(
                42.0f,
                58.0f,
                -12.0f,
                sh.harold.library.entity.EntityPose.CROUCHING,
                Map.of(sh.harold.library.entity.EquipmentSlot.MAIN_HAND, axe),
                true,
                false
        );

        port.renderSharedFrame(performance);

        assertEquals(42.0f, mannequin.getPosition().yaw());
        assertEquals(58.0f, mannequin.getHeadRotation());
        assertEquals(-12.0f, mannequin.getPosition().pitch());
        assertEquals(EntityPose.SNEAKING, mannequin.getPose());
        assertEquals(Key.key("minecraft", "iron_axe"), mannequin.getEquipment(EquipmentSlot.MAIN_HAND).material().key());
        assertTrue(mannequin.getLivingEntityMeta().isHandActive());
        assertEquals(PlayerHand.MAIN, mannequin.getLivingEntityMeta().getActiveHand());

        ItemDescriptor book = new ItemDescriptor(Key.key("minecraft", "book"), 1);
        NpcRenderFrame newestBase = new NpcRenderFrame(
                -30.0f,
                -30.0f,
                4.0f,
                sh.harold.library.entity.EntityPose.STANDING,
                Map.of(sh.harold.library.entity.EquipmentSlot.OFF_HAND, book),
                false,
                false
        );
        port.updateBaseFrame(newestBase);
        port.restoreNativePresentation().toCompletableFuture().join();

        assertEquals(-30.0f, mannequin.getPosition().yaw());
        assertEquals(-30.0f, mannequin.getHeadRotation());
        assertEquals(4.0f, mannequin.getPosition().pitch());
        assertEquals(EntityPose.STANDING, mannequin.getPose());
        assertTrue(mannequin.getEquipment(EquipmentSlot.MAIN_HAND).isAir());
        assertEquals(Key.key("minecraft", "book"), mannequin.getEquipment(EquipmentSlot.OFF_HAND).material().key());
        assertFalse(mannequin.getLivingEntityMeta().isHandActive());

        port.close();
        port.close();
    }

    @Test
    void cachedLosResultDoesNotAdvanceHysteresisWithoutANewProbeEpoch() {
        UUID npcId = new UUID(0L, 100L);
        UUID viewerId = new UUID(0L, 1L);
        NpcBehaviorActor actor = new NpcBehaviorActor(npcId, 0.0f, 0.0f, new NpcBehaviorRenderPort() {
        });
        actor.updateActorView(Vec3.ZERO, Optional.of(SpaceId.of("test", "instance")), 1);
        NpcAttentionSpec attention = NpcAttentionSpec.builder()
                .idleResponse(NpcAttentionResponse.sustain(NpcSustainMode.STEADY))
                .build();
        actor.configure(NpcBehaviorProfile.builder().attention(attention).build());
        actor.tick(0);

        actor.observeViewer(observation(viewerId, true), 1L);
        actor.tick(1);
        assertEquals(viewerId, actor.snapshot().canonicalTarget().orElseThrow());

        for (int tick = 2; tick <= 40; tick++) {
            actor.observeViewer(observation(viewerId, false), 2L);
            actor.tick(tick);
        }
        assertEquals(viewerId, actor.snapshot().canonicalTarget().orElseThrow(),
                "one failed Minestom probe must count once even when its cached result is republished every tick");

        for (int tick = 41; tick <= 48; tick++) {
            actor.observeViewer(observation(viewerId, false), 3L);
            actor.tick(tick);
        }
        assertEquals(viewerId, actor.snapshot().canonicalTarget().orElseThrow());

        for (int tick = 49; tick <= 56; tick++) {
            actor.observeViewer(observation(viewerId, false), 4L);
            actor.tick(tick);
        }
        assertTrue(actor.snapshot().canonicalTarget().isEmpty(), "the third distinct failed probe releases attention");
    }

    private static NpcAttentionStack.Observation observation(UUID viewerId, boolean lineOfSight) {
        return new NpcAttentionStack.Observation(
                viewerId,
                true,
                true,
                4.0,
                0.0,
                lineOfSight,
                new NpcAttentionStack.GazeTarget(0.0f, 0.0f)
        );
    }
}
