package sh.harold.library.example.minestom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;
import sh.harold.library.menu.ActionVerb;
import sh.harold.library.menu.Menu;
import sh.harold.library.menu.MenuClick;
import sh.harold.library.menu.MenuContext;
import sh.harold.library.menu.MenuDefinition;
import sh.harold.library.menu.MenuInteraction;
import sh.harold.library.menu.MenuSlot;
import sh.harold.library.menu.MenuSlotAction;
import sh.harold.library.message.MessageBlock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinestomMessageShowcaseTest {

    @Test
    void catalogHasNineGoldensThirteenSynthesizedAndUniqueIds() {
        MinestomMessageShowcase showcase = new MinestomMessageShowcase();

        assertEquals(9, showcase.goldens().size());
        assertEquals(13, showcase.synthesized().size());
        assertEquals(22, showcase.entries().size());
        assertEquals(22, showcase.entries().stream().map(MinestomMessageShowcase.Entry::id).distinct().count());
        assertTrue(showcase.goldens().stream().allMatch(entry -> entry.source().isPresent()));
        assertTrue(showcase.synthesized().stream().allMatch(entry -> entry.source().isEmpty()));
        assertTrue(showcase.goldens().stream()
                .flatMap(entry -> entry.source().orElseThrow().messageSha256().stream())
                .allMatch(hash -> hash.matches("[0-9a-f]{64}")));
    }

    @Test
    void panelKeepsStableTabsCountsAndOneSendActionPerSpecimen() {
        MinestomMessageShowcase showcase = new MinestomMessageShowcase();
        Menu panel = showcase.panel(entry -> { });
        Set<String> labels = showcase.entries().stream()
                .map(MinestomMessageShowcase.Entry::label)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals("Message Showcase", flatten(panel.title()));
        Set<String> visibleText = slots(panel)
                .flatMap(slot -> Stream.concat(
                        Stream.of(flatten(slot.title())),
                        slot.lore().stream().map(MinestomMessageShowcaseTest::flatten)
                ))
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(visibleText.contains("Corpus Goldens"));
        assertTrue(visibleText.contains("9 observed messages"));
        assertTrue(visibleText.contains("Synthesized"));
        assertTrue(visibleText.contains("13 house-style messages"));

        List<MenuSlot> specimens = slots(panel)
                .filter(slot -> labels.contains(flatten(slot.title())))
                .toList();
        assertEquals(labels, specimens.stream()
                .map(slot -> flatten(slot.title()))
                .collect(java.util.stream.Collectors.toSet()));
        specimens.forEach(slot -> {
            assertEquals(1, slot.interactions().size(), flatten(slot.title()));
            MenuInteraction interaction = slot.interactions().get(MenuClick.LEFT);
            assertNotNull(interaction, flatten(slot.title()));
            assertEquals(ActionVerb.VIEW, interaction.verb());
            assertEquals("send", interaction.promptLabel());
            assertEquals("CLICK to send!", flatten(slot.lore().getLast()));
        });
    }

    @Test
    void selectingAButtonClosesBeforeSendingOnlyThatSpecimen() {
        MinestomMessageShowcase showcase = new MinestomMessageShowcase();
        List<String> events = new ArrayList<>();
        Menu panel = showcase.panel(entry -> events.add("send:" + entry.id()));
        MenuSlot bankInterest = slots(panel)
                .filter(slot -> flatten(slot.title()).equals("Bank Interest"))
                .findFirst()
                .orElseThrow();
        MenuSlotAction.Execute execute = assertInstanceOf(
                MenuSlotAction.Execute.class,
                bankInterest.interactions().get(MenuClick.LEFT).action()
        );
        MenuContext context = new MenuContext(
                MenuClick.LEFT,
                panel.initialFrameId(),
                new HashMap<>(),
                recordingControls(events)
        );

        execute.action().execute(context);

        assertEquals(List.of("close", "send:bank-interest"), events);
    }

    @Test
    void screenshotGoldenRetainsTranscriptHashAndMatchingSixtyFourCharacterDividers() {
        MinestomMessageShowcase.Entry entry = entry(new MinestomMessageShowcase(), "bonus-tree-gift");
        MinestomMessageShowcase.Source source = entry.source().orElseThrow();
        MinestomMessageShowcase.BlockOutput output = assertInstanceOf(
                MinestomMessageShowcase.BlockOutput.class,
                entry.outputs().getFirst()
        );
        List<MessageBlock.Entry> lines = output.block().entries();
        MessageBlock.TitleEntry first = assertInstanceOf(MessageBlock.TitleEntry.class, lines.getFirst());
        MessageBlock.TitleEntry last = assertInstanceOf(MessageBlock.TitleEntry.class, lines.getLast());

        assertEquals(MinestomMessageShowcase.SCREENSHOT_SHA256, source.screenshotSha256().orElseThrow());
        assertTrue(source.messageSha256().isEmpty());
        assertEquals(64, first.text().length());
        assertEquals(MinestomMessageShowcase.DIVIDER, first.text());
        assertEquals(first.text(), last.text());
    }

    @Test
    void synthesizedSpecimensCoverTheDeclaredFeatureMatrix() {
        MinestomMessageShowcase showcase = new MinestomMessageShowcase();
        EnumSet<MinestomMessageShowcase.Feature> covered =
                EnumSet.noneOf(MinestomMessageShowcase.Feature.class);
        showcase.synthesized().forEach(entry -> covered.addAll(entry.features()));

        assertEquals(EnumSet.allOf(MinestomMessageShowcase.Feature.class), covered);
    }

    @Test
    void playerDirectoryPaginatesThroughChatWithoutRepeatingRows() {
        Component first = MinestomMessageShowcase.chatMenuPage(1).component();
        Component second = MinestomMessageShowcase.chatMenuPage(2).component();
        Component third = MinestomMessageShowcase.chatMenuPage(3).component();

        assertTrue(flatten(first).contains("Hqrxld - Hub"));
        assertEquals(List.of("/testmessages chatmenu 2"), clickCommands(first));

        assertTrue(flatten(second).contains("David - Hunter's Camp"));
        assertTrue(flatten(second).contains("Tim - Workshop"));
        assertEquals(
                List.of("/testmessages chatmenu 1", "/testmessages chatmenu 3"),
                clickCommands(second)
        );

        assertTrue(flatten(third).contains("Sirius - Dark Auction"));
        assertEquals(List.of("/testmessages chatmenu 2"), clickCommands(third));
        assertEquals(flatten(third), flatten(MinestomMessageShowcase.chatMenuPage(99).component()));
    }

    @Test
    void everySpecimenMatchesTheApprovedAdventureTreeAndDeliverySurface() throws IOException {
        String actual = snapshot(new MinestomMessageShowcase());
        Path generated = Path.of("build", "generated-snapshots", "message-showcase.snap");
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, actual, StandardCharsets.UTF_8);

        try (InputStream input = getClass().getResourceAsStream("/message-showcase.snap")) {
            assertNotNull(input, "Missing message-showcase.snap; generated candidate at " + generated.toAbsolutePath());
            String expected = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertEquals(expected, actual);
        }
    }

    private static MinestomMessageShowcase.Entry entry(MinestomMessageShowcase showcase, String id) {
        return showcase.entries().stream()
                .filter(entry -> entry.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static Stream<MenuSlot> slots(Menu menu) {
        return menu.frameIds().stream().flatMap(frameId -> menu.frame(frameId).slots().stream());
    }

    private static MenuContext.SessionControls recordingControls(List<String> events) {
        return new MenuContext.SessionControls() {
            @Override
            public void refresh() {
                events.add("refresh");
            }

            @Override
            public void open(MenuDefinition menu) {
                events.add("open");
            }

            @Override
            public void back() {
                events.add("back");
            }

            @Override
            public void close() {
                events.add("close");
            }
        };
    }

    private static List<String> clickCommands(Component component) {
        List<String> commands = new ArrayList<>();
        collectClickCommands(component, commands);
        return commands;
    }

    private static void collectClickCommands(Component component, List<String> commands) {
        if (component.clickEvent() != null) {
            commands.add(component.clickEvent().value());
        }
        component.children().forEach(child -> collectClickCommands(child, commands));
    }

    private static String snapshot(MinestomMessageShowcase showcase) {
        StringBuilder output = new StringBuilder();
        for (MinestomMessageShowcase.Entry entry : showcase.entries()) {
            output.append("ENTRY ").append(entry.id())
                    .append(" | ").append(entry.origin())
                    .append(" | ").append(entry.label()).append('\n');
            output.append("FEATURES ").append(new TreeSet<>(entry.features())).append('\n');
            entry.source().ifPresent(source -> {
                output.append("SOURCE ").append(source.description()).append('\n');
                source.messageSha256().forEach(hash -> output.append("SOURCE_MESSAGE ").append(hash).append('\n'));
                source.screenshotSha256().ifPresent(hash -> output.append("SOURCE_SCREENSHOT ").append(hash).append('\n'));
            });
            for (int index = 0; index < entry.outputs().size(); index++) {
                appendOutput(output, index, entry.outputs().get(index));
            }
            output.append("END_ENTRY\n\n");
        }
        return output.toString();
    }

    private static void appendOutput(
            StringBuilder output,
            int index,
            MinestomMessageShowcase.Output specimen
    ) {
        if (specimen instanceof MinestomMessageShowcase.ChatOutput chat) {
            output.append("OUTPUT ").append(index).append(" CHAT\n");
            appendComponent(output, chat.message().component(), 0);
            return;
        }
        if (specimen instanceof MinestomMessageShowcase.BlockOutput block) {
            output.append("OUTPUT ").append(index)
                    .append(" BLOCK centered=").append(block.block().centered()).append('\n');
            appendComponent(output, block.block().component(), 0);
            return;
        }
        if (specimen instanceof MinestomMessageShowcase.ActionBarOutput actionBar) {
            output.append("OUTPUT ").append(index).append(" ACTION_BAR\n");
            appendComponent(output, actionBar.message().actionBarComponent(), 0);
            return;
        }
        MinestomMessageShowcase.TitleOutput title =
                assertInstanceOf(MinestomMessageShowcase.TitleOutput.class, specimen);
        output.append("OUTPUT ").append(index)
                .append(" TITLE fadeIn=").append(title.message().fadeIn())
                .append(" stay=").append(title.message().stay())
                .append(" fadeOut=").append(title.message().fadeOut()).append('\n');
        output.append("TITLE\n");
        appendComponent(output, title.message().title(), 0);
        output.append("SUBTITLE\n");
        appendComponent(output, title.message().subtitle(), 0);
    }

    private static void appendComponent(StringBuilder output, Component component, int indent) {
        if (indent > 0 && isPassThrough(component)) {
            component.children().forEach(child -> appendComponent(output, child, indent));
            return;
        }

        output.append(" ".repeat(indent)).append(componentLabel(component));
        appendStyle(output, component.style());
        ClickEvent click = component.clickEvent();
        if (click != null) {
            output.append(" click=").append(click.action().name().toLowerCase())
                    .append('(').append(escape(click.value())).append(')');
        }
        output.append('\n');

        HoverEvent<?> hover = component.hoverEvent();
        if (hover != null) {
            output.append(" ".repeat(indent + 2)).append("hover {\n");
            if (hover.value() instanceof Component hoverComponent) {
                appendComponent(output, hoverComponent, indent + 4);
            } else {
                output.append(" ".repeat(indent + 4)).append(escape(String.valueOf(hover.value()))).append('\n');
            }
            output.append(" ".repeat(indent + 2)).append("}\n");
        }
        component.children().forEach(child -> appendComponent(output, child, indent + 2));
    }

    private static boolean isPassThrough(Component component) {
        return component instanceof TextComponent text
                && text.content().isEmpty()
                && component.style().isEmpty()
                && component.clickEvent() == null
                && component.hoverEvent() == null;
    }

    private static String componentLabel(Component component) {
        if (component instanceof TextComponent text) {
            return "text(\"" + escape(text.content()) + "\")";
        }
        return component.getClass().getSimpleName();
    }

    private static void appendStyle(StringBuilder output, Style style) {
        TextColor color = style.color();
        if (color != null) {
            output.append(" color=").append(color.asHexString().toUpperCase());
        }
        appendDecoration(output, style, TextDecoration.BOLD, "bold");
        appendDecoration(output, style, TextDecoration.ITALIC, "italic");
        appendDecoration(output, style, TextDecoration.UNDERLINED, "underlined");
        appendDecoration(output, style, TextDecoration.STRIKETHROUGH, "strikethrough");
        appendDecoration(output, style, TextDecoration.OBFUSCATED, "obfuscated");
    }

    private static void appendDecoration(
            StringBuilder output,
            Style style,
            TextDecoration decoration,
            String label
    ) {
        if (style.decoration(decoration) == TextDecoration.State.TRUE) {
            output.append(' ').append(label);
        }
    }

    private static String flatten(Component component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof TextComponent value) {
            text.append(value.content());
        }
        component.children().forEach(child -> text.append(flatten(child)));
        return text.toString();
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (char character : value.toCharArray()) {
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '\"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20 || character > 0x7e) {
                        escaped.append("\\u")
                                .append(String.format("%04X", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}

