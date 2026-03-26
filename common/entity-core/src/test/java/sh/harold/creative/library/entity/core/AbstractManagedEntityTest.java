package sh.harold.creative.library.entity.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.entity.CommonEntityFlags;
import sh.harold.creative.library.entity.EntitySpec;
import sh.harold.creative.library.entity.EntityTransform;
import sh.harold.creative.library.entity.EntityTypes;
import sh.harold.creative.library.entity.InteractionKind;
import sh.harold.creative.library.entity.InteractorRef;

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
        assertThrows(IllegalStateException.class, () -> entity.interactionHandler(context -> {
        }));
    }

    @Test
    void latestInteractionHandlerWins() {
        TestManagedEntity entity = new TestManagedEntity();
        List<String> calls = new ArrayList<>();

        entity.interactionHandler(context -> calls.add("first"));
        entity.interactionHandler(context -> calls.add("second"));
        entity.handleInteraction(new InteractorRef(UUID.randomUUID()), InteractionKind.PRIMARY);

        assertEquals(List.of("second"), calls);
    }

    @Test
    void initialFlagsAndTagsStayImmutable() {
        TestManagedEntity entity = new TestManagedEntity();

        assertEquals(Component.text("Guide"), entity.customName().orElseThrow());
        assertTrue(entity.tags().contains(Key.key("creative", "service")));
        assertThrows(UnsupportedOperationException.class, () -> entity.tags().add(Key.key("creative", "other")));
    }

    private static final class TestManagedEntity extends AbstractManagedEntity {

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
