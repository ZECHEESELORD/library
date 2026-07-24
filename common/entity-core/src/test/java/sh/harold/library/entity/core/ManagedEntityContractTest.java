package sh.harold.library.entity.core;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import sh.harold.library.entity.EntityInteractionAction;
import sh.harold.library.entity.EntityInteractionContext;
import sh.harold.library.entity.EntityInteractionHandler;
import sh.harold.library.entity.EntityInteractionResult;
import sh.harold.library.entity.EntitySpec;
import sh.harold.library.entity.EntityTransform;
import sh.harold.library.entity.EntityTypes;
import sh.harold.library.entity.InteractionHand;
import sh.harold.library.entity.InteractorRef;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManagedEntityContractTest {

    @Test
    void unsupportedCapabilitiesStayAbsentUntilRegistered() {
        ContractEntity entity = new ContractEntity();

        assertTrue(entity.capability(MutableCapability.class).isEmpty());

        entity.installMutableCapability();

        assertTrue(entity.capability(MutableCapability.class).isPresent());
        assertTrue(entity.capability(CharSequence.class).isEmpty());
    }

    @Test
    void interactionDispatchUsesCurrentHandlerAndStopsAfterClear() {
        ContractEntity entity = new ContractEntity();
        AtomicReference<EntityInteractionContext> seen = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        InteractorRef interactor = new InteractorRef(UUID.randomUUID());

        entity.interactionHandler(EntityInteractionHandler.observing(context -> {
            seen.set(context);
            calls.incrementAndGet();
        }, EntityInteractionResult.CONSUME));
        assertEquals(EntityInteractionResult.CONSUME, entity.handleUse(interactor, InteractionHand.OFF_HAND));
        entity.clearInteractionHandler();
        assertEquals(EntityInteractionResult.PASS, entity.handleAttack(interactor));

        assertEquals(1, calls.get());
        assertEquals(EntityInteractionAction.USE, seen.get().action());
        assertEquals(InteractionHand.OFF_HAND, seen.get().hand().orElseThrow());
        assertEquals(interactor, seen.get().interactor());
    }

    @Test
    void useDeduplicationDoesNotBlockNextTick() {
        ContractEntity entity = new ContractEntity();
        AtomicInteger calls = new AtomicInteger();
        InteractorRef interactor = new InteractorRef(UUID.randomUUID());

        entity.interactionHandler(EntityInteractionHandler.observing(
                context -> calls.incrementAndGet(),
                EntityInteractionResult.PASS
        ));
        entity.interactionNowNanos(100L);
        entity.handleUse(interactor, InteractionHand.OFF_HAND);
        entity.interactionNowNanos(50_000_100L);
        entity.handleUse(interactor, InteractionHand.MAIN_HAND);

        assertEquals(2, calls.get());
    }

    @Test
    void wrongThreadGuardFailsFastForBaseAndCapabilityMutations() {
        ContractEntity entity = new ContractEntity();
        entity.installMutableCapability();
        entity.ownerThread(false);

        IllegalStateException baseFailure = assertThrows(IllegalStateException.class, () -> entity.customName(Component.text("Off thread")));
        IllegalStateException capabilityFailure = assertThrows(
                IllegalStateException.class,
                () -> entity.capability(MutableCapability.class).orElseThrow().mutate()
        );

        assertEquals("Entity mutations must run on the owner thread", baseFailure.getMessage());
        assertEquals("Entity mutations must run on the owner thread", capabilityFailure.getMessage());
    }

    @Test
    void despawnedEntitiesRejectFurtherCapabilityMutation() {
        ContractEntity entity = new ContractEntity();
        entity.installMutableCapability();
        entity.despawn();

        assertThrows(IllegalStateException.class, () -> entity.capability(MutableCapability.class).orElseThrow().mutate());
    }

    @FunctionalInterface
    private interface MutableCapability {
        void mutate();
    }

    private static final class ContractEntity extends AbstractManagedEntity {
        private boolean ownerThread = true;
        private long interactionNowNanos = 100L;

        private ContractEntity() {
            super(
                    UUID.randomUUID(),
                    EntitySpec.builder(EntityTypes.VILLAGER)
                            .transform(EntityTransform.at(0.0, 0.0, 0.0))
                            .build()
            );
        }

        private void installMutableCapability() {
            registerCapability(MutableCapability.class, this::requireMutable);
        }

        private void ownerThread(boolean ownerThread) {
            this.ownerThread = ownerThread;
        }

        private void interactionNowNanos(long interactionNowNanos) {
            this.interactionNowNanos = interactionNowNanos;
        }

        @Override
        protected void assertOwnerThread() {
            if (!ownerThread) {
                throw new IllegalStateException("Entity mutations must run on the owner thread");
            }
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
