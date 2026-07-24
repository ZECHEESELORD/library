package sh.harold.library.entity.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import sh.harold.library.entity.CommonEntityFlags;
import sh.harold.library.entity.EntityInteractionAction;
import sh.harold.library.entity.EntityInteractionHandler;
import sh.harold.library.entity.EntityInteractionResult;
import sh.harold.library.entity.EntitySpec;
import sh.harold.library.entity.EntityTransform;
import sh.harold.library.entity.EntityTypes;
import sh.harold.library.entity.InteractionHand;
import sh.harold.library.entity.InteractorRef;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractManagedEntityTest {

    @Test
    void specValidationRejectsNullSpec() {
        assertThrows(NullPointerException.class, () -> EntitySpecValidator.validate(null));
    }

    @Test
    void capabilityLookupReturnsOnlyRegisteredTypes() {
        TestManagedEntity entity = new TestManagedEntity();

        entity.installRunnableCapability();

        assertTrue(entity.capability(Runnable.class).isPresent());
        assertTrue(entity.capability(CharSequence.class).isEmpty());
    }

    @Test
    void lifecycleGuardsRejectMutationAfterDespawn() {
        TestManagedEntity entity = new TestManagedEntity();

        entity.despawn();

        assertFalse(entity.spawned());
        assertThrows(IllegalStateException.class, () -> entity.glowing(true));
        assertThrows(IllegalStateException.class, () -> entity.interactionHandler(
                EntityInteractionHandler.observing(context -> {
                }, EntityInteractionResult.PASS)
        ));
    }

    @Test
    void latestInteractionHandlerWins() {
        TestManagedEntity entity = new TestManagedEntity();
        List<String> calls = new ArrayList<>();

        entity.interactionHandler(EntityInteractionHandler.observing(context -> calls.add("first"), EntityInteractionResult.PASS));
        entity.interactionHandler(EntityInteractionHandler.observing(context -> calls.add("second"), EntityInteractionResult.PASS));
        entity.handleUse(new InteractorRef(UUID.randomUUID()), InteractionHand.MAIN_HAND);

        assertEquals(List.of("second"), calls);
    }

    @Test
    void sameTickDualHandUseIsDeduplicatedWithoutSuppressingAttack() {
        TestManagedEntity entity = new TestManagedEntity();
        List<EntityInteractionAction> calls = new ArrayList<>();
        InteractorRef interactor = new InteractorRef(UUID.randomUUID());

        entity.interactionHandler(EntityInteractionHandler.observing(
                context -> calls.add(context.action()),
                EntityInteractionResult.CONSUME
        ));
        entity.interactionNowNanos(1_000L);
        assertEquals(EntityInteractionResult.CONSUME, entity.handleUse(interactor, InteractionHand.MAIN_HAND));
        entity.interactionNowNanos(2_000L);
        assertEquals(EntityInteractionResult.CONSUME, entity.handleUse(interactor, InteractionHand.OFF_HAND));
        assertEquals(EntityInteractionResult.CONSUME, entity.handleAttack(interactor));
        entity.interactionNowNanos(50_001_000L);
        entity.handleUse(interactor, InteractionHand.OFF_HAND);

        assertEquals(List.of(
                EntityInteractionAction.USE,
                EntityInteractionAction.ATTACK,
                EntityInteractionAction.USE
        ), calls);
    }

    @Test
    void debounceIsScopedPerInteractor() {
        TestManagedEntity entity = new TestManagedEntity();
        List<UUID> calls = new ArrayList<>();
        InteractorRef first = new InteractorRef(UUID.randomUUID());
        InteractorRef second = new InteractorRef(UUID.randomUUID());

        entity.interactionHandler(EntityInteractionHandler.observing(
                context -> calls.add(context.interactor().uniqueId()),
                EntityInteractionResult.PASS
        ));
        entity.interactionNowNanos(5_000L);
        entity.handleUse(first, InteractionHand.MAIN_HAND);
        entity.interactionNowNanos(6_000L);
        entity.handleUse(second, InteractionHand.MAIN_HAND);

        assertEquals(List.of(first.uniqueId(), second.uniqueId()), calls);
    }

    @Test
    void initialFlagsAndTagsStayImmutable() {
        TestManagedEntity entity = new TestManagedEntity();

        assertEquals(Component.text("Guide"), entity.customName().orElseThrow());
        assertTrue(entity.tags().contains(Key.key("creative", "service")));
        assertThrows(UnsupportedOperationException.class, () -> entity.tags().add(Key.key("creative", "other")));
    }

    private static final class TestManagedEntity extends AbstractManagedEntity {
        private long interactionNowNanos = 1_000L;

        private TestManagedEntity() {
            super(
                    UUID.randomUUID(),
                    EntitySpec.builder(EntityTypes.VILLAGER)
                            .transform(new EntityTransform(1.0, 2.0, 3.0, 90.0f, 15.0f))
                            .flags(CommonEntityFlags.builder().customName(Component.text("Guide")).customNameVisible(true).build())
                            .tag(Key.key("creative", "service"))
                            .build()
            );
            applyInitialState();
        }

        private void installRunnableCapability() {
            registerCapability(Runnable.class, () -> {
            });
        }

        private void interactionNowNanos(long interactionNowNanos) {
            this.interactionNowNanos = interactionNowNanos;
        }

        @Override
        protected long interactionNowNanos() {
            return interactionNowNanos;
        }

        @Override
        protected void doTeleport(EntityTransform transform) {
        }

        @Override
        protected void doCustomName(Component customName) {
        }

        @Override
        protected void doClearCustomName() {
        }

        @Override
        protected void doCustomNameVisible(boolean visible) {
        }

        @Override
        protected void doGlowing(boolean glowing) {
        }

        @Override
        protected void doSilent(boolean silent) {
        }

        @Override
        protected void doGravity(boolean gravity) {
        }

        @Override
        protected void doInvulnerable(boolean invulnerable) {
        }

        @Override
        protected void doDespawn() {
        }
    }
}
