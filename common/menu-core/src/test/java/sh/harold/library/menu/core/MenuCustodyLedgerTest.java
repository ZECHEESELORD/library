package sh.harold.library.menu.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.menu.MenuCustodySnapshot;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.MenuStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuCustodyLedgerTest {

    @Test
    void exactNativeValueMovesWithoutPresentationReconstruction() {
        MenuCustodyLedger<NativeItem> ledger = new MenuCustodyLedger<>(Map.of("center", 31));
        NativeItem nativeItem = new NativeItem("diamond_sword", 7, "all-components");
        MenuStack presentation = MenuStack.builder(MenuIcon.vanilla("diamond_sword")).name("Sword").amount(7).build();

        MenuCustodyLedger.PreparedTransfer<NativeItem> acquire = ledger.prepareAcquire(
                nativeItem, presentation, 4, new MenuCustodyLedger.Destination.Cursor());
        ledger.commit(acquire);
        assertEquals(nativeItem, ledger.cursor().orElseThrow().nativeItem());

        MenuCustodyLedger.PreparedTransfer<NativeItem> store = ledger.prepareTransfer(
                new MenuCustodyLedger.Source.Cursor(), new MenuCustodyLedger.Destination.Target("center"));
        ledger.commit(store);

        assertTrue(ledger.cursor().isEmpty());
        assertEquals(nativeItem, ledger.target("center").orElseThrow().nativeItem());
        MenuCustodySnapshot snapshot = ledger.snapshot();
        assertEquals(7, snapshot.targets().get("center").presentation().amount());
        assertEquals(4, snapshot.targets().get("center").originalViewerSlot());
    }

    @Test
    void staleOrDuplicateCommitCannotCreateAnotherOwner() {
        MenuCustodyLedger<String> ledger = new MenuCustodyLedger<>(Map.of("center", 31));
        MenuCustodyLedger.PreparedTransfer<String> acquire = ledger.prepareAcquire(
                "exact", stack(), 0, new MenuCustodyLedger.Destination.Cursor());
        ledger.commit(acquire);

        assertThrows(IllegalStateException.class, () -> ledger.commit(acquire));
        assertEquals("exact", ledger.cursor().orElseThrow().nativeItem());
        assertTrue(ledger.target("center").isEmpty());
    }

    @Test
    void occupiedDestinationRejectsBeforeLedgerMutation() {
        MenuCustodyLedger<String> ledger = new MenuCustodyLedger<>(Map.of("center", 31));
        ledger.commit(ledger.prepareAcquire("first", stack(), 0,
                new MenuCustodyLedger.Destination.Target("center")));

        assertThrows(IllegalStateException.class, () -> ledger.prepareAcquire(
                "second", stack(), 1, new MenuCustodyLedger.Destination.Target("center")));
        assertEquals("first", ledger.target("center").orElseThrow().nativeItem());
        assertFalse(ledger.empty());
    }

    @Test
    void releaseRetiresTheOnlyLedgerOwner() {
        MenuCustodyLedger<String> ledger = new MenuCustodyLedger<>(Map.of("center", 31));
        ledger.commit(ledger.prepareAcquire("exact", stack(), 2,
                new MenuCustodyLedger.Destination.Target("center")));
        ledger.commit(ledger.prepareTransfer(new MenuCustodyLedger.Source.Target("center"),
                new MenuCustodyLedger.Destination.Released()));

        assertTrue(ledger.empty());
        assertEquals(MenuCustodySnapshot.EMPTY, ledger.snapshot());
    }

    @Test
    void transferCannotBeCommittedToAnotherLedger() {
        MenuCustodyLedger<String> first = new MenuCustodyLedger<>(Map.of("center", 31));
        MenuCustodyLedger<String> second = new MenuCustodyLedger<>(Map.of("center", 31));
        MenuCustodyLedger.PreparedTransfer<String> acquire = first.prepareAcquire(
                "exact", stack(), 2, new MenuCustodyLedger.Destination.Target("center"));

        assertThrows(IllegalArgumentException.class, () -> second.commit(acquire));
        assertTrue(first.empty());
        assertTrue(second.empty());
    }

    @Test
    void ledgerRejectsAmbiguousTargetConfigurationAndInvalidOrigins() {
        assertThrows(IllegalArgumentException.class,
                () -> new MenuCustodyLedger<>(Map.of("first", 4, "second", 4)));
        MenuCustodyLedger<String> ledger = new MenuCustodyLedger<>(Map.of("center", 31));
        assertThrows(IllegalArgumentException.class, () -> ledger.prepareAcquire(
                "exact", stack(), -1, new MenuCustodyLedger.Destination.Target("center")));
        assertTrue(ledger.empty());
    }

    @Test
    void targetLookupUsesTheConfiguredSlotIndex() {
        Map<String, Integer> targets = new LinkedHashMap<>();
        targets.put("left", 20);
        targets.put("right", 24);
        MenuCustodyLedger<String> ledger = new MenuCustodyLedger<>(targets);

        assertEquals(List.of("left", "right"), List.copyOf(ledger.targetSlots().keySet()));
        assertEquals("left", ledger.targetAt(20).orElseThrow());
        assertEquals("right", ledger.targetAt(24).orElseThrow());
        assertTrue(ledger.targetAt(22).isEmpty());
    }

    @Test
    void randomizedWholeStackTransfersConserveEveryExactAsset() {
        MenuCustodyLedger<NativeItem> ledger = new MenuCustodyLedger<>(
                Map.of("left", 20, "center", 22, "right", 24));
        Random random = new Random(0xC0570D1L);
        int acquiredAmount = 0;
        int releasedAmount = 0;
        int nextId = 1;

        for (int step = 0; step < 10_000; step++) {
            List<MenuCustodyLedger.Source> sources = sources(ledger);
            List<MenuCustodyLedger.Destination> heldDestinations = heldDestinations(ledger);
            if (sources.isEmpty() || (!heldDestinations.isEmpty() && random.nextBoolean())) {
                MenuCustodyLedger.Destination destination =
                        heldDestinations.get(random.nextInt(heldDestinations.size()));
                int amount = 1 + random.nextInt(64);
                NativeItem item = new NativeItem("asset-" + nextId++, amount, "exact-" + step);
                MenuStack presentation = MenuStack.builder(MenuIcon.vanilla("stone"))
                        .name(item.type())
                        .amount(amount)
                        .build();
                ledger.commit(ledger.prepareAcquire(item, presentation, random.nextInt(36), destination));
                acquiredAmount += amount;
            } else {
                MenuCustodyLedger.Source source = sources.get(random.nextInt(sources.size()));
                MenuCustodyLedger.Entry<NativeItem> entry = entry(ledger, source);
                List<MenuCustodyLedger.Destination> destinations = new ArrayList<>(heldDestinations);
                destinations.add(new MenuCustodyLedger.Destination.Released());
                MenuCustodyLedger.Destination destination = destinations.get(random.nextInt(destinations.size()));
                MenuCustodyLedger.PreparedTransfer<NativeItem> transfer =
                        ledger.prepareTransfer(source, destination);
                ledger.commit(transfer);
                if (destination instanceof MenuCustodyLedger.Destination.Released) {
                    releasedAmount += entry.nativeItem().amount();
                }
                assertThrows(IllegalStateException.class, () -> ledger.commit(transfer));
            }

            List<MenuCustodyLedger.Entry<NativeItem>> held = entries(ledger);
            int heldAmount = held.stream().mapToInt(value -> value.nativeItem().amount()).sum();
            Set<String> assetIds = new HashSet<>();
            held.forEach(value -> assertTrue(assetIds.add(value.nativeItem().type())));
            assertEquals(acquiredAmount, releasedAmount + heldAmount);
        }
    }

    private static MenuStack stack() {
        return MenuStack.builder(MenuIcon.vanilla("stone")).name("Stone").build();
    }

    private static List<MenuCustodyLedger.Source> sources(MenuCustodyLedger<NativeItem> ledger) {
        List<MenuCustodyLedger.Source> sources = new ArrayList<>();
        if (ledger.cursor().isPresent()) {
            sources.add(new MenuCustodyLedger.Source.Cursor());
        }
        ledger.targetSlots().keySet().forEach(key -> {
            if (ledger.target(key).isPresent()) {
                sources.add(new MenuCustodyLedger.Source.Target(key));
            }
        });
        return sources;
    }

    private static List<MenuCustodyLedger.Destination> heldDestinations(MenuCustodyLedger<NativeItem> ledger) {
        List<MenuCustodyLedger.Destination> destinations = new ArrayList<>();
        if (ledger.cursor().isEmpty()) {
            destinations.add(new MenuCustodyLedger.Destination.Cursor());
        }
        ledger.targetSlots().keySet().forEach(key -> {
            if (ledger.target(key).isEmpty()) {
                destinations.add(new MenuCustodyLedger.Destination.Target(key));
            }
        });
        return destinations;
    }

    private static List<MenuCustodyLedger.Entry<NativeItem>> entries(MenuCustodyLedger<NativeItem> ledger) {
        List<MenuCustodyLedger.Entry<NativeItem>> entries = new ArrayList<>();
        ledger.cursor().ifPresent(entries::add);
        ledger.targetSlots().keySet().forEach(key -> ledger.target(key).ifPresent(entries::add));
        return entries;
    }

    private static MenuCustodyLedger.Entry<NativeItem> entry(
            MenuCustodyLedger<NativeItem> ledger,
            MenuCustodyLedger.Source source
    ) {
        return switch (source) {
            case MenuCustodyLedger.Source.Cursor ignored -> ledger.cursor().orElseThrow();
            case MenuCustodyLedger.Source.Target target -> ledger.target(target.key()).orElseThrow();
        };
    }

    private record NativeItem(String type, int amount, String metadata) {
    }
}
