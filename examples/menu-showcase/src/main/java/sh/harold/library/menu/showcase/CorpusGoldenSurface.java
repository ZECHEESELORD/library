package sh.harold.library.menu.showcase;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import sh.harold.library.menu.ActionVerb;
import sh.harold.library.menu.CanvasMenuBuilder;
import sh.harold.library.menu.Menu;
import sh.harold.library.menu.MenuButton;
import sh.harold.library.menu.MenuClick;
import sh.harold.library.menu.MenuDisplayItem;
import sh.harold.library.menu.MenuFrame;
import sh.harold.library.menu.MenuGeometry;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.MenuItem;
import sh.harold.library.menu.MenuService;
import sh.harold.library.menu.MenuSlot;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CorpusGoldenSurface {

    private static final String RESOURCE = "/sh/harold/library/menu/showcase/corpus-goldens.json";
    private static final GsonComponentSerializer COMPONENTS = GsonComponentSerializer.gson();
    private static final Pattern PROMPT = Pattern.compile(
            "^(?:(SHIFT)\\s+)?(?:(LEFT|RIGHT)[-\\s]*)?CLICK(?:\\s+HERE)?\\s+(TO|FOR)\\s+(.+)!$",
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, Integer> NORMALIZED_SLOT_OVERRIDES = Map.of(
            "skyblock-menu:48", 46,
            "bazaar-oddities:48", 46,
            "community-shop:49", 47,
            "heart-of-forest:49", 41,
            "skills:53", 52,
            "museum-milestones:53", 52,
            "chocolate-milestones:53", 52,
            "personal-bank:35", 34);
    private static final List<CorpusGoldenSurface> ALL = loadAll();

    private final String id;
    private final String label;
    private final String surfaceSha256;
    private final String title;
    private final int rows;
    private final List<CapturedItem> items;
    private final List<CapturedChrome> chrome;

    private CorpusGoldenSurface(
            String id,
            String label,
            String surfaceSha256,
            String title,
            int rows,
            List<CapturedItem> items,
            List<CapturedChrome> chrome
    ) {
        this.id = requireText(id, "id");
        this.label = requireText(label, "label");
        this.surfaceSha256 = SourceItemReference.requireSha256(surfaceSha256, "surfaceSha256");
        this.title = requireText(title, "title");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be between 1 and 6");
        }
        this.rows = rows;
        this.items = List.copyOf(items);
        this.chrome = List.copyOf(chrome);
        if (this.items.isEmpty()) {
            throw new IllegalArgumentException("items cannot be empty");
        }
        Set<Integer> slots = new HashSet<>();
        for (CapturedItem item : this.items) {
            if (item.sourceSlot() >= rows * 9) {
                throw new IllegalArgumentException("Source slot " + item.sourceSlot() + " is outside " + id);
            }
            if (!slots.add(item.sourceSlot())) {
                throw new IllegalArgumentException("Duplicate source slot " + item.sourceSlot() + " in " + id);
            }
        }
        for (CapturedChrome group : this.chrome) {
            for (int slot : group.slots()) {
                if (slot >= rows * 9) {
                    throw new IllegalArgumentException("Chrome slot " + slot + " is outside " + id);
                }
                if (!slots.add(slot)) {
                    throw new IllegalArgumentException("Duplicate source slot " + slot + " in " + id);
                }
            }
        }
    }

    static List<CorpusGoldenSurface> all() {
        return ALL;
    }

    String id() {
        return id;
    }

    String label() {
        return label;
    }

    SourceReference sourceReference() {
        List<SourceItemReference> references = new ArrayList<>();
        items.forEach(item -> references.add(new SourceItemReference(item.sha256(), item.sourceSlot())));
        chrome.forEach(group -> group.slots().forEach(
                slot -> references.add(new SourceItemReference(group.sha256(), slot))));
        references.sort(Comparator.comparingInt(SourceItemReference::slot));
        return new SourceReference(surfaceSha256, references);
    }

    Menu build(MenuService menus) {
        CanvasMenuBuilder canvas = menus.canvas().title(title).rows(rows);
        int footerStart = (rows - 1) * 9;
        Set<Integer> sourceOwnedSlots = sourceOwnedSlots(footerStart);
        Set<Integer> placedSlots = new HashSet<>();
        boolean backPlaced = false;

        for (CapturedItem item : items) {
            String itemTitle = plain(item.name()).trim();
            if (itemTitle.equalsIgnoreCase("Close")) {
                continue;
            }
            if (itemTitle.equalsIgnoreCase("Go Back")) {
                if (backPlaced) {
                    continue;
                }
                backPlaced = true;
            }

            int targetSlot = normalizedSlot(item, footerStart, sourceOwnedSlots, placedSlots);
            if (!placedSlots.add(targetSlot)) {
                throw new IllegalStateException("Normalized slot collision at " + targetSlot + " in " + id);
            }
            canvas.place(targetSlot, item.toMenuItem());
        }
        Menu built = canvas.build();
        return withCapturedBackground(built, Set.copyOf(placedSlots));
    }

    private Menu withCapturedBackground(Menu delegate, Set<Integer> placedSlots) {
        int closeSlot = (delegate.rows() - 1) * 9 + 4;
        Set<Integer> sourceItemSlots = items.stream()
                .map(CapturedItem::sourceSlot)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<Integer, String> chromeBySlot = new LinkedHashMap<>();
        chrome.forEach(group -> group.slots().forEach(slot -> chromeBySlot.put(slot, group.icon())));
        Map<Integer, String> capturedChrome = Map.copyOf(chromeBySlot);
        boolean retainHouseClose = !id.equals("confirmation");
        return new Menu() {
            @Override
            public Component title() {
                return delegate.title();
            }

            @Override
            public String initialFrameId() {
                return delegate.initialFrameId();
            }

            @Override
            public Set<String> frameIds() {
                return delegate.frameIds();
            }

            @Override
            public MenuFrame frame(String frameId) {
                MenuFrame frame = delegate.frame(frameId);
                List<MenuSlot> slots = frame.slots().stream()
                        .map(slot -> {
                            int index = slot.slot();
                            if (placedSlots.contains(index) || (retainHouseClose && index == closeSlot)) {
                                return slot;
                            }
                            String chromeIcon = capturedChrome.get(index);
                            if (chromeIcon != null) {
                                return chrome(index, chromeIcon);
                            }
                            if (sourceItemSlots.contains(index)) {
                                return slot;
                            }
                            return empty(index);
                        })
                        .toList();
                return new MenuFrame(frame.title(), slots);
            }

            @Override
            public MenuGeometry geometry() {
                return delegate.geometry();
            }

            @Override
            public int rows() {
                return delegate.rows();
            }
        };
    }

    private static MenuSlot chrome(int slot, String icon) {
        return new MenuSlot(slot, MenuIcon.vanilla(icon),
                Component.text(" "), List.of(), false, Map.of());
    }

    private static MenuSlot empty(int slot) {
        return new MenuSlot(slot, MenuIcon.vanilla("air"),
                Component.empty(), List.of(), false, Map.of());
    }

    private Set<Integer> sourceOwnedSlots(int footerStart) {
        Set<Integer> slots = new HashSet<>();
        for (CapturedItem item : items) {
            String itemTitle = plain(item.name()).trim();
            if (itemTitle.equalsIgnoreCase("Close") || itemTitle.equalsIgnoreCase("Go Back")) {
                continue;
            }
            if (!isReserved(item.sourceSlot(), footerStart)
                    && !NORMALIZED_SLOT_OVERRIDES.containsKey(id + ":" + item.sourceSlot())) {
                slots.add(item.sourceSlot());
            }
        }
        return slots;
    }

    private int normalizedSlot(
            CapturedItem item,
            int footerStart,
            Set<Integer> sourceOwnedSlots,
            Set<Integer> placedSlots
    ) {
        Integer override = NORMALIZED_SLOT_OVERRIDES.get(id + ":" + item.sourceSlot());
        if (override != null) {
            return override;
        }
        String itemTitle = plain(item.name()).trim();
        if (!itemTitle.equalsIgnoreCase("Go Back") && !isReserved(item.sourceSlot(), footerStart)) {
            return item.sourceSlot();
        }
        List<Integer> utilitySlots = List.of(
                footerStart + 1,
                footerStart + 2,
                footerStart + 5,
                footerStart + 6,
                footerStart + 7);
        return utilitySlots.stream()
                .filter(slot -> slot >= footerStart && slot < rows * 9)
                .filter(slot -> !sourceOwnedSlots.contains(slot))
                .filter(slot -> !placedSlots.contains(slot))
                .min(Comparator.comparingInt(slot -> Math.abs(item.sourceSlot() - slot)))
                .orElseThrow(() -> new IllegalStateException(
                        "No normalized footer slot for source slot " + item.sourceSlot() + " in " + id));
    }

    private static boolean isReserved(int slot, int footerStart) {
        return slot == footerStart + 3 || slot == footerStart + 4 || slot == footerStart + 8;
    }

    private static List<CorpusGoldenSurface> loadAll() {
        try (InputStream input = CorpusGoldenSurface.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing golden menu resource " + RESOURCE);
            }
            JsonObject root = COMPONENTS.serializer().fromJson(
                    new InputStreamReader(input, StandardCharsets.UTF_8), JsonObject.class);
            if (requiredInt(root, "schemaVersion") != 2) {
                throw new IllegalStateException("Unsupported golden menu resource schema");
            }
            List<CorpusGoldenSurface> surfaces = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (JsonElement element : requiredArray(root, "surfaces")) {
                JsonObject surface = element.getAsJsonObject();
                String id = requiredString(surface, "id");
                if (!ids.add(id)) {
                    throw new IllegalArgumentException("Duplicate golden surface id " + id);
                }
                List<CapturedItem> items = new ArrayList<>();
                for (JsonElement itemElement : requiredArray(surface, "items")) {
                    items.add(parseItem(itemElement.getAsJsonObject()));
                }
                List<CapturedChrome> chrome = new ArrayList<>();
                for (JsonElement chromeElement : requiredArray(surface, "chrome")) {
                    JsonObject group = chromeElement.getAsJsonObject();
                    List<Integer> slots = new ArrayList<>();
                    for (JsonElement slot : requiredArray(group, "slots")) {
                        slots.add(slot.getAsInt());
                    }
                    chrome.add(new CapturedChrome(
                            requiredString(group, "sha256"),
                            requiredString(group, "icon"),
                            slots));
                }
                surfaces.add(new CorpusGoldenSurface(
                        id,
                        requiredString(surface, "label"),
                        requiredString(surface, "surfaceSha256"),
                        requiredString(surface, "title"),
                        requiredInt(surface, "rows"),
                        items,
                        chrome));
            }
            if (surfaces.size() != 20) {
                throw new IllegalStateException("Expected 20 corpus golden surfaces, got " + surfaces.size());
            }
            return List.copyOf(surfaces);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not load golden menu resource", exception);
        }
    }

    private static CapturedItem parseItem(JsonObject item) {
        List<Component> lore = new ArrayList<>();
        for (JsonElement line : requiredArray(item, "lore")) {
            lore.add(COMPONENTS.deserializeFromTree(line));
        }
        JsonElement texture = item.get("texture");
        return new CapturedItem(
                requiredInt(item, "slot"),
                requiredString(item, "sha256"),
                requiredString(item, "icon"),
                texture == null || texture.isJsonNull() ? null : texture.getAsString(),
                requiredInt(item, "amount"),
                item.get("glow").getAsBoolean(),
                COMPONENTS.deserializeFromTree(item.get("name")),
                lore);
    }

    private static JsonArray requiredArray(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(property + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static int requiredInt(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException(property + " must be an integer");
        }
        return value.getAsInt();
    }

    private static String requiredString(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException(property + " must be a string");
        }
        return requireText(value.getAsString(), property);
    }

    private static String requireText(String value, String property) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) {
            throw new IllegalArgumentException(property + " cannot be blank");
        }
        return value;
    }

    private static String plain(Component component) {
        StringBuilder text = new StringBuilder();
        appendPlain(component, text);
        return text.toString();
    }

    private static void appendPlain(Component component, StringBuilder text) {
        if (component instanceof TextComponent value) {
            text.append(value.content());
        }
        component.children().forEach(child -> appendPlain(child, text));
    }

    private record CapturedItem(
            int sourceSlot,
            String sha256,
            String icon,
            String texture,
            int amount,
            boolean glow,
            Component name,
            List<Component> lore
    ) {

        private CapturedItem {
            if (sourceSlot < 0 || sourceSlot > 53) {
                throw new IllegalArgumentException("sourceSlot must be between 0 and 53");
            }
            sha256 = SourceItemReference.requireSha256(sha256, "sha256");
            icon = requireText(icon, "icon");
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be greater than zero");
            }
            name = Objects.requireNonNull(name, "name");
            lore = List.copyOf(lore);
        }

        MenuItem toMenuItem() {
            AuthoredLore authored = authoredLore();
            String title = plain(name).trim();
            boolean implicitBack = title.equalsIgnoreCase("Go Back") || title.equalsIgnoreCase("Cancel");
            if (authored.prompts().isEmpty() && !implicitBack) {
                MenuDisplayItem.Builder builder = MenuDisplayItem.builder(menuIcon())
                        .name(name)
                        .amount(amount)
                        .glow(glow);
                authored.sections().forEach(section -> builder.section(rows -> rows.componentLines(section)));
                return builder.build();
            }

            MenuButton.Builder builder = MenuButton.builder(menuIcon())
                    .name(name)
                    .amount(amount)
                    .glow(glow);
            authored.sections().forEach(section -> builder.section(rows -> rows.componentLines(section)));
            for (CapturedPrompt prompt : authored.prompts()) {
                applyPrompt(builder, prompt);
            }
            if (implicitBack && authored.prompts().isEmpty()) {
                builder.onLeftClick(ActionVerb.BACK, title.equalsIgnoreCase("Cancel") ? "cancel" : "go back",
                                context -> { })
                        .skipPrompt();
            }
            return builder.build();
        }

        private MenuIcon menuIcon() {
            return texture == null ? MenuIcon.vanilla(icon) : MenuIcon.customHead(texture);
        }

        private AuthoredLore authoredLore() {
            List<List<Component>> sections = new ArrayList<>();
            Map<MenuClick, CapturedPrompt> prompts = new LinkedHashMap<>();
            List<Component> current = new ArrayList<>();
            for (Component line : lore) {
                String text = plain(line);
                if (text.isEmpty()) {
                    flush(current, sections);
                    continue;
                }
                CapturedPrompt prompt = parsePrompt(text);
                if (prompt != null) {
                    flush(current, sections);
                    prompts.put(prompt.click(), prompt);
                    continue;
                }
                current.add(text.isBlank() ? Component.text("\u200B").append(line) : line);
            }
            flush(current, sections);
            return new AuthoredLore(List.copyOf(sections), List.copyOf(prompts.values()));
        }

        private static void flush(List<Component> current, List<List<Component>> sections) {
            if (!current.isEmpty()) {
                sections.add(List.copyOf(current));
                current.clear();
            }
        }
    }

    private static CapturedPrompt parsePrompt(String text) {
        Matcher matcher = PROMPT.matcher(text.trim());
        if (!matcher.matches()) {
            return null;
        }
        boolean shift = matcher.group(1) != null;
        String direction = matcher.group(2);
        MenuClick click = shift
                ? (direction != null && direction.equalsIgnoreCase("RIGHT")
                    ? MenuClick.SHIFT_RIGHT
                    : MenuClick.SHIFT_LEFT)
                : (direction != null && direction.equalsIgnoreCase("RIGHT")
                    ? MenuClick.RIGHT
                    : MenuClick.LEFT);
        String preposition = matcher.group(3);
        String label = matcher.group(4).trim();
        if (preposition.equalsIgnoreCase("FOR")) {
            label = switch (label.toLowerCase(Locale.ROOT)) {
                case "options" -> "view options";
                case "hub warp" -> "use hub warp";
                default -> label;
            };
        }
        return new CapturedPrompt(click, inferVerb(label), label);
    }

    private static ActionVerb inferVerb(String label) {
        String normalized = label.toLowerCase(Locale.ROOT);
        if (normalized.contains("confirm")) {
            return ActionVerb.CONFIRM;
        }
        if (normalized.contains("claim")) {
            return ActionVerb.CLAIM;
        }
        if (normalized.contains("toggle")) {
            return ActionVerb.TOGGLE;
        }
        if (normalized.contains("buy") || normalized.contains("gems")) {
            return ActionVerb.BUY;
        }
        if (normalized.contains("open") || normalized.contains("warp")) {
            return ActionVerb.OPEN;
        }
        if (normalized.contains("pick") || normalized.contains("select")) {
            return ActionVerb.SELECT;
        }
        if (normalized.contains("manage") || normalized.contains("sell")
                || normalized.contains("deposit") || normalized.contains("withdraw")
                || normalized.contains("clear") || normalized.contains("remove")
                || normalized.contains("save") || normalized.contains("set amount")
                || normalized.contains("create")) {
            return ActionVerb.MANAGE;
        }
        return ActionVerb.VIEW;
    }

    private static void applyPrompt(MenuButton.Builder builder, CapturedPrompt prompt) {
        switch (prompt.click()) {
            case LEFT -> builder.onLeftClick(prompt.verb(), prompt.label(), context -> { });
            case SHIFT_LEFT -> builder.onShiftLeftClick(prompt.verb(), prompt.label(), context -> { });
            case RIGHT -> builder.onRightClick(prompt.verb(), prompt.label(), context -> { });
            case SHIFT_RIGHT -> builder.onShiftRightClick(prompt.verb(), prompt.label(), context -> { });
        }
    }

    private record CapturedPrompt(MenuClick click, ActionVerb verb, String label) {
    }

    private record CapturedChrome(String sha256, String icon, List<Integer> slots) {

        private CapturedChrome {
            sha256 = SourceItemReference.requireSha256(sha256, "sha256");
            icon = requireText(icon, "icon");
            slots = List.copyOf(slots);
            if (slots.isEmpty()) {
                throw new IllegalArgumentException("chrome slots cannot be empty");
            }
            for (int slot : slots) {
                if (slot < 0 || slot > 53) {
                    throw new IllegalArgumentException("chrome slot must be between 0 and 53");
                }
            }
        }
    }

    private record AuthoredLore(List<List<Component>> sections, List<CapturedPrompt> prompts) {
    }
}
