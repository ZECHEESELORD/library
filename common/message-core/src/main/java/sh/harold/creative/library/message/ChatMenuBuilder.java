package sh.harold.creative.library.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ChatMenuBuilder {
    private static final int DEFAULT_PAGE_SIZE = 8;

    private final String title;
    private final List<Row> rows = new ArrayList<>();
    private int titleColor = 0x57C7FF;
    private int page = 1;
    private int pageSize = DEFAULT_PAGE_SIZE;
    private String emptyLine = "No entries.";
    private String previousCommand;
    private String nextCommand;

    ChatMenuBuilder(String title) {
        this.title = requireText(title, "title");
    }

    public ChatMenuBuilder titleColor(int rgbHex) {
        if (rgbHex < 0x000000 || rgbHex > 0xFFFFFF) {
            throw new IllegalArgumentException("rgbHex must be between 0x000000 and 0xFFFFFF");
        }
        this.titleColor = rgbHex;
        return this;
    }

    public ChatMenuBuilder page(int page) {
        this.page = Math.max(1, page);
        return this;
    }

    public ChatMenuBuilder pageSize(int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        this.pageSize = pageSize;
        return this;
    }

    public ChatMenuBuilder emptyLine(String emptyLine) {
        this.emptyLine = requireText(emptyLine, "emptyLine");
        return this;
    }

    public ChatMenuBuilder previousCommand(String previousCommand) {
        this.previousCommand = cleanCommand(previousCommand);
        return this;
    }

    public ChatMenuBuilder nextCommand(String nextCommand) {
        this.nextCommand = cleanCommand(nextCommand);
        return this;
    }

    public ChatMenuBuilder row(String template, SlotBinding... slots) {
        rows.add(new Row(requireText(template, "template"), List.of(slots == null ? new SlotBinding[0] : slots)));
        return this;
    }

    public MessageBlock build() {
        MessageBlockBuilder block = Message.block().title(title.toUpperCase(Locale.ROOT), titleColor);
        if (rows.isEmpty()) {
            block.line(emptyLine);
            return block.build();
        }

        int pages = Math.max(1, (rows.size() + pageSize - 1) / pageSize);
        int currentPage = Math.min(page, pages);
        int start = (currentPage - 1) * pageSize;
        int end = Math.min(rows.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            Row row = rows.get(index);
            block.line(row.template(), row.slots().toArray(SlotBinding[]::new));
        }
        if (pages > 1) {
            block.blank();
            block.line(
                    "{previous} Page {page}/{pages} {next}",
                    Message.slot("previous", pageControl("[PREV]", previousCommand, currentPage > 1)),
                    Message.slot("page", currentPage),
                    Message.slot("pages", pages),
                    Message.slot("next", pageControl("[NEXT]", nextCommand, currentPage < pages))
            );
        }
        return block.build();
    }

    private static MessageValue pageControl(String label, String command, boolean enabled) {
        MessageValue value = Message.value(Component.text(label, enabled ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY));
        if (enabled && command != null && !command.isBlank()) {
            return value.click(Click.runCommand(command));
        }
        return value;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }

    private static String cleanCommand(String command) {
        if (command == null || command.isBlank()) {
            return null;
        }
        return command.trim();
    }

    private record Row(String template, List<SlotBinding> slots) {
    }
}
