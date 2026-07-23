package sh.harold.library.menu.fabric;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sh.harold.library.menu.MenuClick;
import sh.harold.library.menu.MenuCustodyDecision;
import sh.harold.library.menu.MenuCustodyDestination;
import sh.harold.library.menu.MenuCustodyFailure;
import sh.harold.library.menu.MenuCustodyGesture;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.MenuStack;
import sh.harold.library.menu.MenuViewerSlot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricMenuCustodyTest {

    private static final MenuStack PRESENTATION = MenuStack.builder(MenuIcon.vanilla("nether_star"))
            .name("Exact stack")
            .amount(7)
            .build();

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void policyLifecycleReentryInvalidatesDecisionBeforeSourceMutation() {
        FabricMenuTaskScheduler scheduler = new FabricMenuTaskScheduler();
        FabricMenuCallbackGate callbacks = new FabricMenuCallbackGate(scheduler::scheduleNextTick);
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("center");
        ItemStack original = exactStack();
        nativeAccess.viewer[5] = original.copy();
        MenuViewerSlot viewerSlot = new MenuViewerSlot(1L, 5, PRESENTATION);
        MenuCustodyGesture gesture = new MenuCustodyGesture.ViewerClick(
                viewerSlot, MenuClick.LEFT, false);
        AtomicInteger lifecycleRuns = new AtomicInteger();

        long callbackGeneration = callbacks.generation();
        MenuCustodyDecision decision = callbacks.invoke(() -> {
            assertTrue(callbacks.defer(lifecycleRuns::incrementAndGet));
            return MenuCustodyDecision.move(MenuCustodyDestination.cursor());
        });
        if (callbacks.unchanged(callbackGeneration)) {
            custody.transact(gesture, decision, original.copy(), nativeAccess);
        }

        assertFalse(callbacks.unchanged(callbackGeneration));
        assertEquals(0, lifecycleRuns.get());
        assertTrue(custody.empty());
        assertExact(original, nativeAccess.viewer[5]);
        assertTrue(nativeAccess.cursor.isEmpty());

        scheduler.tick();
        assertEquals(1, lifecycleRuns.get());
    }

    @Test
    void failedCallbackDropsItsDeferredLifecycleAction() {
        FabricMenuTaskScheduler scheduler = new FabricMenuTaskScheduler();
        FabricMenuCallbackGate callbacks = new FabricMenuCallbackGate(scheduler::scheduleNextTick);
        AtomicInteger lifecycleRuns = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> callbacks.invoke(() -> {
            assertTrue(callbacks.defer(lifecycleRuns::incrementAndGet));
            throw new IllegalStateException("policy failed");
        }));

        scheduler.tick();

        assertEquals(0, lifecycleRuns.get());
    }

    @Test
    void wholeStackRoundTripPreservesEveryNativeComponent() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("center");
        ItemStack original = exactStack();
        nativeAccess.viewer[5] = original.copy();

        FabricMenuCustody.Outcome acquired = acquire(
                custody, nativeAccess, 1L, 5, original, MenuCustodyDestination.cursor());
        assertTrue(acquired.committed());
        assertTrue(nativeAccess.viewer[5].isEmpty());
        assertExact(original, nativeAccess.cursor);
        assertNotSame(original, custody.ledger().cursor().orElseThrow().nativeItem());

        FabricMenuCustody.Outcome inserted = custody.transact(
                new MenuCustodyGesture.TargetClick("center", MenuClick.RIGHT, false),
                MenuCustodyDecision.move(MenuCustodyDestination.target("center")),
                null,
                nativeAccess);
        assertTrue(inserted.committed());
        assertTrue(nativeAccess.cursor.isEmpty());
        assertExact(original, nativeAccess.readTarget("center"));

        FabricMenuCustody.Outcome withdrawn = custody.transact(
                new MenuCustodyGesture.TargetClick("center", MenuClick.LEFT, false),
                MenuCustodyDecision.move(MenuCustodyDestination.cursor()),
                null,
                nativeAccess);
        assertTrue(withdrawn.committed());
        assertExact(original, nativeAccess.cursor);

        MenuViewerSlot empty = new MenuViewerSlot(2L, 8, null);
        FabricMenuCustody.Outcome released = custody.transact(
                new MenuCustodyGesture.ViewerClick(empty, MenuClick.RIGHT, false),
                MenuCustodyDecision.move(MenuCustodyDestination.viewerSlot(empty)),
                ItemStack.EMPTY,
                nativeAccess);

        assertTrue(released.committed());
        assertTrue(custody.empty());
        assertTrue(nativeAccess.cursor.isEmpty());
        assertExact(original, nativeAccess.viewer[8]);
    }

    @Test
    void staleViewerObservationCannotAcquireOrOverwriteAnything() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("center");
        ItemStack observed = exactStack();
        nativeAccess.viewer[4] = stone();

        FabricMenuCustody.Outcome outcome = acquire(
                custody, nativeAccess, 1L, 4, observed, MenuCustodyDestination.cursor());

        assertEquals(MenuCustodyFailure.STALE_INPUT, outcome.failure());
        assertTrue(custody.empty());
        assertExact(stone(), nativeAccess.viewer[4]);
        assertTrue(nativeAccess.cursor.isEmpty());
    }

    @Test
    void acquireRejectsForeignPhysicalTargetBeforeClearingViewerSource() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("center");
        ItemStack original = exactStack();
        ItemStack foreign = stone();
        nativeAccess.viewer[4] = original.copy();
        nativeAccess.targets.put("center", foreign.copy());

        FabricMenuCustody.Outcome outcome = acquire(
                custody, nativeAccess, 1L, 4, original, MenuCustodyDestination.target("center"));

        assertEquals(MenuCustodyFailure.NATIVE_MUTATION_FAILED, outcome.failure());
        assertTrue(custody.empty());
        assertExact(original, nativeAccess.viewer[4]);
        assertExact(foreign, nativeAccess.readTarget("center"));
    }

    @Test
    void cursorTransferRejectsForeignPhysicalTargetBeforeClearingCursor() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("center");
        ItemStack original = exactStack();
        ItemStack foreign = stone();
        nativeAccess.viewer[4] = original.copy();
        assertTrue(acquire(
                custody, nativeAccess, 1L, 4, original, MenuCustodyDestination.cursor()).committed());
        nativeAccess.targets.put("center", foreign.copy());

        FabricMenuCustody.Outcome outcome = custody.transact(
                new MenuCustodyGesture.TargetClick("center", MenuClick.LEFT, false),
                MenuCustodyDecision.move(MenuCustodyDestination.target("center")),
                null,
                nativeAccess);

        assertEquals(MenuCustodyFailure.NATIVE_MUTATION_FAILED, outcome.failure());
        assertTrue(custody.ledger().cursor().isPresent());
        assertExact(original, nativeAccess.cursor);
        assertExact(foreign, nativeAccess.readTarget("center"));
    }

    @Test
    void nativeViewValidationDetectsTargetDriftWithoutOverwritingIt() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("center");
        ItemStack foreign = stone();

        assertTrue(custody.nativeViewReady(nativeAccess));
        nativeAccess.targets.put("center", foreign.copy());

        assertFalse(custody.nativeViewReady(nativeAccess));
        assertExact(foreign, nativeAccess.readTarget("center"));
    }

    @Test
    void nativeViewValidationRejectsAnUnownedPhysicalCursorWithoutClearingIt() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("center");
        ItemStack foreign = stone();
        nativeAccess.cursor = foreign.copy();

        assertFalse(custody.nativeViewReady(nativeAccess));
        assertExact(foreign, nativeAccess.cursor);
        assertTrue(custody.empty());
    }

    @Test
    void nativeViewValidationNeverReconstructsAnOccupiedCustodyTarget() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("center");
        ItemStack original = exactStack();
        ItemStack foreign = stone();
        nativeAccess.viewer[4] = original.copy();
        assertTrue(acquire(
                custody, nativeAccess, 1L, 4, original, MenuCustodyDestination.target("center")).committed());
        nativeAccess.targets.put("center", foreign.copy());

        assertFalse(custody.nativeViewReady(nativeAccess));
        assertExact(foreign, nativeAccess.readTarget("center"));
        assertExact(original, custody.ledger().target("center").orElseThrow().nativeItem());
    }

    @Test
    void dragMustNameExactlyOneTarget() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("one", 11, "two", 15));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("one", "two");
        ItemStack original = exactStack();
        nativeAccess.viewer[3] = original.copy();
        assertTrue(acquire(
                custody, nativeAccess, 1L, 3, original, MenuCustodyDestination.cursor()).committed());

        FabricMenuCustody.Outcome outcome = custody.transact(
                new MenuCustodyGesture.TargetDrag(List.of("one", "two"), MenuClick.LEFT),
                MenuCustodyDecision.move(MenuCustodyDestination.target("one")),
                null,
                nativeAccess);

        assertEquals(MenuCustodyFailure.UNSUPPORTED_GESTURE, outcome.failure());
        assertExact(original, nativeAccess.cursor);
        assertTrue(nativeAccess.readTarget("one").isEmpty());
        assertTrue(nativeAccess.readTarget("two").isEmpty());
    }

    @Test
    void interactiveOriginRejectsAnOccupiedOriginalSlot() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("center");
        ItemStack original = exactStack();
        nativeAccess.viewer[3] = original.copy();
        assertTrue(acquire(
                custody, nativeAccess, 1L, 3, original, MenuCustodyDestination.cursor()).committed());
        nativeAccess.viewer[3] = stone();

        FabricMenuCustody.Outcome outcome = custody.transact(
                new MenuCustodyGesture.OutsideClick(MenuClick.LEFT),
                MenuCustodyDecision.move(MenuCustodyDestination.origin()),
                null,
                nativeAccess);

        assertEquals(MenuCustodyFailure.OCCUPIED_DESTINATION, outcome.failure());
        assertExact(original, nativeAccess.cursor);
        assertExact(stone(), nativeAccess.viewer[3]);
    }

    @Test
    void lifecycleSettlementFallsBackOnlyWithinOrdinaryStorageThenDrops() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("center");
        ItemStack original = exactStack();
        nativeAccess.viewer[5] = original.copy();
        assertTrue(acquire(
                custody, nativeAccess, 1L, 5, original, MenuCustodyDestination.target("center")).committed());
        for (int slot = 0; slot < nativeAccess.viewerSize(); slot++) {
            nativeAccess.viewer[slot] = stone();
        }

        List<FabricMenuCustody.Outcome> outcomes = custody.settle(
                MenuCustodyGesture.SettleReason.CLOSE, nativeAccess);

        assertEquals(1, outcomes.size());
        assertTrue(outcomes.getFirst().committed());
        assertTrue(outcomes.getFirst().snapshot().empty());
        assertTrue(custody.empty());
        assertTrue(nativeAccess.viewer[36].isEmpty());
        assertEquals(1, nativeAccess.dropped.size());
        assertExact(original, nativeAccess.dropped.getFirst());
        assertTrue(nativeAccess.readTarget("center").isEmpty());
    }

    @Test
    void deathSettlementReturnsCursorAndTargetBeforeVanillaLoot() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("center");
        ItemStack cursorItem = exactStack();
        ItemStack targetItem = exactStack();
        targetItem.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Target custody"));
        nativeAccess.viewer[1] = cursorItem.copy();
        nativeAccess.viewer[2] = targetItem.copy();
        assertTrue(acquire(
                custody, nativeAccess, 1L, 1, cursorItem, MenuCustodyDestination.cursor()).committed());
        assertTrue(acquire(
                custody, nativeAccess, 2L, 2, targetItem, MenuCustodyDestination.target("center")).committed());

        List<FabricMenuCustody.Outcome> outcomes = custody.settle(
                MenuCustodyGesture.SettleReason.DEATH, nativeAccess);

        assertEquals(1, outcomes.size());
        assertTrue(outcomes.getFirst().committed());
        assertTrue(outcomes.getFirst().snapshot().empty());
        assertTrue(custody.empty());
        assertExact(cursorItem, nativeAccess.viewer[1]);
        assertExact(targetItem, nativeAccess.viewer[2]);
        assertTrue(nativeAccess.cursor.isEmpty());
        assertTrue(nativeAccess.readTarget("center").isEmpty());
        assertTrue(nativeAccess.dropped.isEmpty());
    }

    @Test
    void deathSettlementDropsFullInventoryOverflowExactlyOnce() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("center");
        ItemStack cursorItem = exactStack();
        ItemStack targetItem = exactStack();
        targetItem.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Target custody"));
        nativeAccess.viewer[1] = cursorItem.copy();
        nativeAccess.viewer[2] = targetItem.copy();
        assertTrue(acquire(
                custody, nativeAccess, 1L, 1, cursorItem, MenuCustodyDestination.cursor()).committed());
        assertTrue(acquire(
                custody, nativeAccess, 2L, 2, targetItem, MenuCustodyDestination.target("center")).committed());
        for (int slot = 0; slot < nativeAccess.viewerSize(); slot++) {
            nativeAccess.viewer[slot] = stone();
        }

        List<FabricMenuCustody.Outcome> outcomes = custody.settle(
                MenuCustodyGesture.SettleReason.DEATH, nativeAccess);

        assertEquals(1, outcomes.size());
        assertTrue(outcomes.getFirst().committed());
        assertTrue(custody.empty());
        assertTrue(nativeAccess.cursor.isEmpty());
        assertTrue(nativeAccess.readTarget("center").isEmpty());
        assertEquals(2, nativeAccess.dropped.size());
        assertExact(cursorItem, nativeAccess.dropped.get(0));
        assertExact(targetItem, nativeAccess.dropped.get(1));
    }

    @Test
    void dropFailureAfterEntityCreationNeverRestoresOrCreditsTheRetiredStack() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("center");
        ItemStack original = exactStack();
        nativeAccess.viewer[5] = original.copy();
        assertTrue(acquire(
                custody, nativeAccess, 1L, 5, original, MenuCustodyDestination.target("center")).committed());
        for (int slot = 0; slot < nativeAccess.viewerSize(); slot++) {
            nativeAccess.viewer[slot] = stone();
        }
        nativeAccess.throwAfterDrop = true;

        List<FabricMenuCustody.Outcome> outcomes = custody.settle(
                MenuCustodyGesture.SettleReason.CLOSE, nativeAccess);

        assertEquals(1, outcomes.size());
        assertEquals(MenuCustodyFailure.NATIVE_MUTATION_FAILED, outcomes.getFirst().failure());
        assertTrue(outcomes.getFirst().snapshot().empty());
        assertTrue(custody.empty());
        assertTrue(nativeAccess.readTarget("center").isEmpty());
        assertEquals(1, nativeAccess.dropped.size());
        assertExact(original, nativeAccess.dropped.getFirst());
        for (int slot = 0; slot < nativeAccess.viewerSize(); slot++) {
            assertExact(stone(), nativeAccess.viewer[slot]);
        }
    }

    @Test
    void alreadyMissingCursorRetiresWithoutCreditingAnotherCopy() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("center");
        ItemStack original = exactStack();
        nativeAccess.viewer[6] = original.copy();
        assertTrue(acquire(
                custody, nativeAccess, 1L, 6, original, MenuCustodyDestination.cursor()).committed());

        nativeAccess.cursor = ItemStack.EMPTY;
        nativeAccess.viewer[6] = original.copy();
        List<FabricMenuCustody.Outcome> outcomes = custody.settle(
                MenuCustodyGesture.SettleReason.CLOSE, nativeAccess);

        assertEquals(1, outcomes.size());
        assertTrue(outcomes.getFirst().committed());
        assertTrue(custody.empty());
        assertExact(original, nativeAccess.viewer[6]);
        assertTrue(nativeAccess.dropped.isEmpty());
    }

    @Test
    void failedCursorReconciliationStopsBeforeSettlingTargets() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("center");
        ItemStack cursorItem = exactStack();
        ItemStack targetItem = exactStack();
        targetItem.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Target copy"));
        nativeAccess.viewer[1] = cursorItem.copy();
        nativeAccess.viewer[2] = targetItem.copy();
        assertTrue(acquire(
                custody, nativeAccess, 1L, 1, cursorItem, MenuCustodyDestination.cursor()).committed());
        assertTrue(acquire(
                custody, nativeAccess, 2L, 2, targetItem, MenuCustodyDestination.target("center")).committed());
        nativeAccess.cursor = stone();

        List<FabricMenuCustody.Outcome> outcomes = custody.settle(
                MenuCustodyGesture.SettleReason.CLOSE, nativeAccess);

        assertEquals(1, outcomes.size());
        assertEquals(MenuCustodyFailure.STALE_INPUT, outcomes.getFirst().failure());
        assertTrue(custody.ledger().cursor().isPresent());
        assertTrue(custody.ledger().target("center").isPresent());
        assertExact(targetItem, nativeAccess.readTarget("center"));
        assertTrue(nativeAccess.viewer[2].isEmpty());
    }

    @Test
    void failedNativeWriteRollsBackBeforeImagesAndLeavesLedgerEmpty() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("center");
        ItemStack original = exactStack();
        nativeAccess.viewer[9] = original.copy();
        nativeAccess.failNextTargetWrite = true;

        FabricMenuCustody.Outcome outcome = acquire(
                custody, nativeAccess, 1L, 9, original, MenuCustodyDestination.target("center"));

        assertEquals(MenuCustodyFailure.NATIVE_MUTATION_FAILED, outcome.failure());
        assertTrue(custody.empty());
        assertExact(original, nativeAccess.viewer[9]);
        assertTrue(nativeAccess.readTarget("center").isEmpty());
    }

    @Test
    void destinationRollbackFailureDoesNotRestoreTheSource() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        FakeNativeAccess nativeAccess = new FakeNativeAccess("center");
        ItemStack original = exactStack();
        nativeAccess.viewer[9] = original.copy();
        nativeAccess.failNextTargetWrite = true;
        nativeAccess.mutateBeforeFailedTargetWrite = true;
        nativeAccess.failTargetRollback = true;

        FabricMenuCustody.Outcome outcome = acquire(
                custody, nativeAccess, 1L, 9, original, MenuCustodyDestination.target("center"));

        assertEquals(MenuCustodyFailure.NATIVE_MUTATION_FAILED, outcome.failure());
        assertTrue(custody.empty());
        assertTrue(nativeAccess.viewer[9].isEmpty());
        assertExact(original, nativeAccess.readTarget("center"));
    }

    private static FabricMenuCustody.Outcome acquire(
            FabricMenuCustody custody,
            FakeNativeAccess nativeAccess,
            long observationId,
            int slot,
            ItemStack observed,
            MenuCustodyDestination destination
    ) {
        MenuViewerSlot viewerSlot = new MenuViewerSlot(observationId, slot, PRESENTATION);
        return custody.transact(
                new MenuCustodyGesture.ViewerClick(viewerSlot, MenuClick.LEFT, false),
                MenuCustodyDecision.move(destination),
                observed.copy(),
                nativeAccess);
    }

    private static ItemStack exactStack() {
        ItemStack stack = new ItemStack(
                net.minecraft.core.Holder.direct(net.minecraft.world.item.Items.NETHER_STAR), 7);
        stack.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Do not flatten me"));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        return stack;
    }

    private static ItemStack stone() {
        return new ItemStack(net.minecraft.core.Holder.direct(net.minecraft.world.item.Items.STONE), 1);
    }

    private static void assertExact(ItemStack expected, ItemStack actual) {
        assertTrue(ItemStack.matches(expected, actual), () -> "Expected exact native stack " + expected + " but got " + actual);
    }

    private static final class FakeNativeAccess implements FabricMenuCustody.NativeAccess {

        private final ItemStack[] viewer = new ItemStack[41];
        private final Map<String, ItemStack> targets = new HashMap<>();
        private final Map<String, ItemStack> targetBases = new HashMap<>();
        private final List<ItemStack> dropped = new ArrayList<>();
        private ItemStack cursor = ItemStack.EMPTY;
        private boolean failNextTargetWrite;
        private boolean mutateBeforeFailedTargetWrite;
        private boolean failTargetRollback;
        private boolean throwAfterDrop;

        private FakeNativeAccess(String... targetKeys) {
            Arrays.fill(viewer, ItemStack.EMPTY);
            for (String targetKey : targetKeys) {
                targets.put(targetKey, ItemStack.EMPTY);
                targetBases.put(targetKey, ItemStack.EMPTY);
            }
        }

        @Override
        public int viewerSize() {
            return 36;
        }

        @Override
        public ItemStack readViewer(int slot) {
            return viewer[slot];
        }

        @Override
        public void writeViewer(int slot, ItemStack item) {
            viewer[slot] = copy(item);
        }

        @Override
        public ItemStack readCursor() {
            return cursor;
        }

        @Override
        public void writeCursor(ItemStack item) {
            cursor = copy(item);
        }

        @Override
        public ItemStack readTarget(String key) {
            return targets.getOrDefault(key, ItemStack.EMPTY);
        }

        @Override
        public boolean targetReady(String key) {
            return ItemStack.matches(readTarget(key), targetBases.getOrDefault(key, ItemStack.EMPTY));
        }

        @Override
        public void writeTarget(String key, ItemStack item) {
            if (failNextTargetWrite) {
                failNextTargetWrite = false;
                if (mutateBeforeFailedTargetWrite) {
                    targets.put(key, copy(item));
                }
                throw new IllegalStateException("simulated native write failure");
            }
            if (failTargetRollback) {
                failTargetRollback = false;
                throw new IllegalStateException("simulated destination rollback failure");
            }
            targets.put(key, copy(item));
        }

        @Override
        public boolean drop(ItemStack item) {
            dropped.add(copy(item));
            if (throwAfterDrop) {
                throw new IllegalStateException("simulated failure after item entity creation");
            }
            return true;
        }

        private static ItemStack copy(ItemStack item) {
            return item == null || item.isEmpty() ? ItemStack.EMPTY : item.copy();
        }
    }
}
