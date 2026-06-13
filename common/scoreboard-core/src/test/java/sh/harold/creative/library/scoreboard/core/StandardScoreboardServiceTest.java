package sh.harold.creative.library.scoreboard.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.scoreboard.ScoreboardFrame;
import sh.harold.creative.library.scoreboard.ScoreboardLine;
import sh.harold.creative.library.scoreboard.ScoreboardSection;
import sh.harold.creative.library.scoreboard.ScoreboardSpec;
import sh.harold.creative.library.scoreboard.TransientPlacement;
import sh.harold.creative.library.scoreboard.TransientSectionSpec;
import sh.harold.creative.library.tick.InstanceConflictPolicy;
import sh.harold.creative.library.tick.KeyedHandle;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardScoreboardServiceTest {
    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_VIEWER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Key BOARD = Key.key("creative", "board");

    @Test
    void rendersBaseSectionsInSpecOrder() {
        StandardScoreboardService service = serviceWithBaseBoard();

        service.show(VIEWER, BOARD);

        assertLines(service, VIEWER, "info-a", "info-b", "activity-a", "activity-b");
    }

    @Test
    void sectionOverrideIsScopedToViewer() {
        StandardScoreboardService service = serviceWithBaseBoard();
        service.show(VIEWER, BOARD);
        service.show(OTHER_VIEWER, BOARD);

        service.overrideSection(VIEWER, "activity", ScoreboardSection.fixed("activity", text("parkour-a"), text("parkour-b"), text("parkour-c")));

        assertLines(service, VIEWER, "info-a", "info-b", "parkour-a", "parkour-b", "parkour-c");
        assertLines(service, OTHER_VIEWER, "info-a", "info-b", "activity-a", "activity-b");
    }

    @Test
    void hideAndShowSectionIsScopedToViewer() {
        StandardScoreboardService service = serviceWithBaseBoard();
        service.show(VIEWER, BOARD);
        service.show(OTHER_VIEWER, BOARD);

        service.hideSection(VIEWER, "info");

        assertLines(service, VIEWER, "activity-a", "activity-b");
        assertLines(service, OTHER_VIEWER, "info-a", "info-b", "activity-a", "activity-b");

        service.showSection(VIEWER, "info");

        assertLines(service, VIEWER, "info-a", "info-b", "activity-a", "activity-b");
    }

    @Test
    void titleOverrideIsScopedToViewerAndClearsExplicitly() {
        StandardScoreboardService service = serviceWithBaseBoard();
        service.show(VIEWER, BOARD);
        service.show(OTHER_VIEWER, BOARD);

        service.overrideTitle(VIEWER, text("Parkour"));

        assertEquals(text("Parkour"), service.render(VIEWER).orElseThrow().title());
        assertEquals(text("Lobby"), service.render(OTHER_VIEWER).orElseThrow().title());

        service.clearTitleOverride(VIEWER);

        assertEquals(text("Lobby"), service.render(VIEWER).orElseThrow().title());
    }

    @Test
    void transientSectionsSupportRelativePlacement() {
        StandardScoreboardService service = serviceWithBaseBoard();
        service.show(VIEWER, BOARD);

        service.pushTransient(VIEWER, transientSpec("top", TransientPlacement.TOP, null, "top"));
        service.pushTransient(VIEWER, transientSpec("bottom", TransientPlacement.BOTTOM, null, "bottom"));
        service.pushTransient(VIEWER, transientSpec("before", TransientPlacement.BEFORE_SECTION, "activity", "before-activity"));
        service.pushTransient(VIEWER, transientSpec("after", TransientPlacement.AFTER_SECTION, "info", "after-info"));

        assertLines(service, VIEWER,
                "top",
                "info-a",
                "info-b",
                "after-info",
                "before-activity",
                "activity-a",
                "activity-b",
                "bottom");
    }

    @Test
    void transientCanReplaceSection() {
        StandardScoreboardService service = serviceWithBaseBoard();
        service.show(VIEWER, BOARD);

        service.pushTransient(VIEWER, transientSpec("replace", TransientPlacement.REPLACE_SECTION, "activity", "parkour-time"));

        assertLines(service, VIEWER, "info-a", "info-b", "parkour-time");
    }

    @Test
    void transientExpiresByTickAndHandleTracksActivity() {
        StandardScoreboardService service = serviceWithBaseBoard();
        service.show(VIEWER, BOARD);
        KeyedHandle handle = service.pushTransient(VIEWER, TransientSectionSpec.builder(Key.key("creative", "flash"))
                .section(ScoreboardSection.fixed("flash", text("flash")))
                .ttlTicks(2)
                .build());

        assertTrue(handle.active());
        service.advance();
        assertTrue(handle.active());
        service.advance();

        assertFalse(handle.active());
        assertLines(service, VIEWER, "info-a", "info-b", "activity-a", "activity-b");
    }

    @Test
    void conflictPoliciesRejectRefreshAndReplaceByKey() {
        StandardScoreboardService service = serviceWithBaseBoard();
        service.show(VIEWER, BOARD);
        Key flash = Key.key("creative", "flash");
        KeyedHandle original = service.pushTransient(VIEWER, TransientSectionSpec.builder(flash)
                .section(ScoreboardSection.fixed("flash", text("one")))
                .ttlTicks(3)
                .build());

        KeyedHandle rejected = service.pushTransient(VIEWER, TransientSectionSpec.builder(flash)
                .section(ScoreboardSection.fixed("flash", text("two")))
                .ttlTicks(5)
                .conflictPolicy(InstanceConflictPolicy.REJECT)
                .build());

        assertFalse(rejected.active());
        assertLines(service, VIEWER, "one", "info-a", "info-b", "activity-a", "activity-b");

        KeyedHandle refreshed = service.pushTransient(VIEWER, TransientSectionSpec.builder(flash)
                .section(ScoreboardSection.fixed("flash", text("two")))
                .ttlTicks(3)
                .conflictPolicy(InstanceConflictPolicy.REFRESH)
                .build());
        service.advance();

        assertTrue(original.active());
        assertTrue(refreshed.active());
        assertLines(service, VIEWER, "one", "info-a", "info-b", "activity-a", "activity-b");

        KeyedHandle replacement = service.pushTransient(VIEWER, TransientSectionSpec.builder(flash)
                .section(ScoreboardSection.fixed("flash", text("three")))
                .ttlTicks(3)
                .conflictPolicy(InstanceConflictPolicy.REPLACE)
                .build());

        assertFalse(original.active());
        assertTrue(replacement.active());
        assertLines(service, VIEWER, "three", "info-a", "info-b", "activity-a", "activity-b");
    }

    @Test
    void lineBudgetPreservesFooterAndTrimsBodyFirst() {
        StandardScoreboardService service = new StandardScoreboardService();
        service.register(ScoreboardSpec.builder(BOARD)
                .fixedSection("body", text("one"), text("two"), text("three"), text("four"))
                .footer(text("footer-a"), text("footer-b"))
                .maxLines(5)
                .build());

        service.show(VIEWER, BOARD);

        assertLines(service, VIEWER, "one", "two", "three", "footer-a", "footer-b");
    }

    @Test
    void duplicateAndBlankLinesAreAllowedInFrames() {
        StandardScoreboardService service = new StandardScoreboardService();
        service.register(ScoreboardSpec.builder(BOARD)
                .fixedSection("body", Component.empty(), text("same"), Component.empty(), text("same"))
                .build());
        service.show(VIEWER, BOARD);

        assertEquals(List.of(Component.empty(), text("same"), Component.empty(), text("same")), components(service.render(VIEWER).orElseThrow()));
    }

    @Test
    void rejectsUnknownScoreboardOnShow() {
        StandardScoreboardService service = new StandardScoreboardService();

        assertThrows(IllegalArgumentException.class, () -> service.show(VIEWER, BOARD));
    }

    @Test
    void perViewerMutationsRequireActiveScoreboard() {
        StandardScoreboardService service = serviceWithBaseBoard();

        assertThrows(IllegalStateException.class, () ->
                service.overrideSection(VIEWER, "activity", ScoreboardSection.fixed("activity", text("line"))));
        assertThrows(IllegalStateException.class, () -> service.hideSection(VIEWER, "activity"));
        assertThrows(IllegalStateException.class, () -> service.pushTransient(VIEWER, transientSpec(
                "flash",
                TransientPlacement.TOP,
                null,
                "flash"
        )));
    }

    private static StandardScoreboardService serviceWithBaseBoard() {
        StandardScoreboardService service = new StandardScoreboardService();
        service.register(ScoreboardSpec.builder(BOARD)
                .title(text("Lobby"))
                .fixedSection("info", text("info-a"), text("info-b"))
                .fixedSection("activity", text("activity-a"), text("activity-b"))
                .build());
        return service;
    }

    private static TransientSectionSpec transientSpec(String key, TransientPlacement placement, String target, String line) {
        TransientSectionSpec.Builder builder = TransientSectionSpec.builder(Key.key("creative", key))
                .section(ScoreboardSection.fixed(key, text(line)))
                .placement(placement)
                .ttlTicks(20);
        if (target != null) {
            builder.targetSectionId(target);
        }
        return builder.build();
    }

    private static void assertLines(StandardScoreboardService service, UUID viewer, String... expected) {
        assertEquals(
                java.util.Arrays.stream(expected).map(StandardScoreboardServiceTest::text).toList(),
                components(service.render(viewer).orElseThrow())
        );
    }

    private static List<Component> components(ScoreboardFrame frame) {
        return frame.lines().stream().map(ScoreboardLine::content).toList();
    }

    private static Component text(String value) {
        return Component.text(value);
    }
}
