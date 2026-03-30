package sh.harold.creative.library.entity.core;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.entity.EntityInteractionContext;
import sh.harold.creative.library.entity.EntitySpec;
import sh.harold.creative.library.entity.EntityTransform;
import sh.harold.creative.library.entity.EntityTypes;
import sh.harold.creative.library.entity.InteractionKind;
import sh.harold.creative.library.entity.InteractorRef;

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

        entity.interactionHandler(context -> {
            seen.set(context);
            calls.incrementAndGet();
        });
        entity.handleInteraction(interactor, InteractionKind.SECONDARY);
        entity.clearInteractionHandler();
        entity.handleInteraction(interactor, InteractionKind.ATTACK);

        assertEquals(1, calls.get());
        assertEquals(InteractionKind.SECONDARY, seen.get().kind());
        assertEquals(interactor, seen.get().interactor());
    }

    @Test
    void interactionDebounceDoesNotBlockAfterWindowExpires() {
        ContractEntity entity = new ContractEntity();
        AtomicInteger calls = new AtomicInteger();
        InteractorRef interactor = new InteractorRef(UUID.randomUUID());

        entity.interactionHandler(context -> calls.incrementAndGet());
        entity.interactionNowNanos(100L);
        entity.handleInteraction(interactor, InteractionKind.SECONDARY);
        entity.interactionNowNanos(100L + 250_000_000L);
        entity.handleInteraction(interactor, InteractionKind.ATTACK);

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
