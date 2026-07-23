package sh.harold.library.menu.fabric;

import org.junit.jupiter.api.Test;
import sh.harold.library.menu.Menu;
import sh.harold.library.menu.core.MenuSessionState;
import sh.harold.library.menu.core.StandardMenuService;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricMenuSettlementTest {

    @Test
    void emptyCustodyNavigationDoesNotInvokeTheSettlementReducer() {
        FabricMenuCustody custody = new FabricMenuCustody(Map.of("center", 13));
        AtomicInteger reducerCalls = new AtomicInteger();

        assertTrue(FabricMenuRuntime.drainCustodyIfHeld(custody, () -> {
            reducerCalls.incrementAndGet();
            return true;
        }));

        assertEquals(0, reducerCalls.get());
    }

    @Test
    void rootBackRestoresTheSettledCustodyTargetOnce() {
        MenuSessionState state = new MenuSessionState(menu("Root"));

        assertRejectedTransitionRestoresOnce(state.prepareBack(), 0);
    }

    @Test
    void sameFrameOpenRestoresTheSettledCustodyTargetOnce() {
        MenuSessionState state = new MenuSessionState(menu("Root"));

        assertRejectedTransitionRestoresOnce(state.prepareOpenFrame(state.frameId()), 0);
    }

    @Test
    void historyDepthRejectionRestoresTheSettledCustodyTargetOnce() {
        Menu child = menu("Child");
        MenuSessionState state = new MenuSessionState(menu("Root"));
        for (int depth = 0; depth < 32; depth++) {
            state.prepareOpenChild(child).orElseThrow().commit();
        }

        assertRejectedTransitionRestoresOnce(state.prepareOpenChild(child), 0);
    }

    @Test
    void failedPreparedTransitionRestoresTheSettledCustodyTargetOnce() {
        MenuSessionState state = new MenuSessionState(menu("Root"));

        assertRejectedTransitionRestoresOnce(
                Optional.of(state.prepareReplaceCurrent(menu("Replacement"))), 1);
    }

    @Test
    void malformedOpenFrameRestoresTheSettledCustodyTarget() {
        MenuSessionState state = new MenuSessionState(menu("Root"));
        FabricMenuSession.SettledCustodyView settledView =
                new FabricMenuSession.SettledCustodyView();
        settledView.markDirty();
        AtomicReference<String> nativeTarget = new AtomicReference<>("EMPTY");
        AtomicInteger quarantines = new AtomicInteger();

        assertThrows(IllegalArgumentException.class, () -> state.prepareOpenFrame("missing"));
        assertTrue(FabricMenuRuntime.restoreFailedTransition(
                true,
                () -> settledView.restore(() -> {
                    nativeTarget.set("BASE");
                    return true;
                }),
                quarantines::incrementAndGet));

        assertEquals("BASE", nativeTarget.get());
        assertEquals("canvas:0", state.frameId());
        assertEquals(0, quarantines.get());
    }

    private static void assertRejectedTransitionRestoresOnce(
            Optional<MenuSessionState.PreparedTransition> prepared,
            int expectedApplyAttempts
    ) {
        FabricMenuSession.SettledCustodyView settledView =
                new FabricMenuSession.SettledCustodyView();
        settledView.markDirty();
        AtomicReference<String> nativeTarget = new AtomicReference<>("EMPTY");
        AtomicInteger applyAttempts = new AtomicInteger();
        AtomicInteger renders = new AtomicInteger();

        prepared.ifPresent(ignored -> applyAttempts.incrementAndGet());
        AtomicInteger quarantines = new AtomicInteger();
        assertTrue(FabricMenuRuntime.restoreFailedTransition(
                true,
                () -> settledView.restore(() -> {
                    renders.incrementAndGet();
                    nativeTarget.set("BASE");
                    return true;
                }),
                quarantines::incrementAndGet));

        assertEquals(expectedApplyAttempts, applyAttempts.get());
        assertEquals("BASE", nativeTarget.get());
        assertEquals(1, renders.get());
        assertFalse(settledView.dirty());
        assertEquals(0, quarantines.get());

        assertTrue(settledView.restore(() -> {
            renders.incrementAndGet();
            return true;
        }));
        assertEquals(1, renders.get());
    }

    private static Menu menu(String title) {
        return new StandardMenuService().canvas()
                .title(title)
                .rows(3)
                .build();
    }
}
