package sh.harold.creative.library.menu.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.core.MenuTrace;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PaperMenuRenderer implements PaperMenuSlotRenderer {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final int CACHE_LIMIT = 4_096;

    private final PaperMenuItemFactory itemFactory;
    private final Map<VisualKey, ItemStack> cache = new LinkedHashMap<>(CACHE_LIMIT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<VisualKey, ItemStack> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    public PaperMenuRenderer() {
        this(new BukkitPaperMenuItemFactory());
    }

    PaperMenuRenderer(PaperMenuItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    @Override
    public ItemStack render(MenuSlot slot) {
        VisualKey key = VisualKey.from(slot);
        ItemStack cached;
        synchronized (cache) {
            cached = cache.get(key);
        }
        if (cached == null) {
            long started = System.nanoTime();
            ItemStack created = createItem(slot);
            long elapsed = System.nanoTime() - started;
            boolean inserted = false;
            synchronized (cache) {
                ItemStack previous = cache.get(key);
                if (previous == null) {
                    cache.put(key, created);
                    cached = created;
                    inserted = true;
                } else {
                    cached = previous;
                }
            }
            if (inserted) {
                MenuTrace.incrementCount("rendererCacheMisses");
                MenuTrace.addDuration("renderer.cacheMissBuild", elapsed);
                MenuTrace.detailIfSlow("renderer-cache-miss", elapsed,
                        () -> "title=" + flatten(slot.title()) + " icon=" + slot.icon().key());
            }
        }
        return cached.clone();
    }

    private ItemStack createItem(MenuSlot slot) {
        ItemStack itemStack = itemFactory.create(slot.icon());
        itemStack.setAmount(slot.amount());
        itemStack.editMeta(meta -> applyMeta(meta, slot));
        return itemStack;
    }

    private static void applyMeta(ItemMeta meta, MenuSlot slot) {
        meta.displayName(slot.title());
        meta.lore(slot.lore());
        meta.setEnchantmentGlintOverride(slot.glow() ? Boolean.TRUE : null);
        meta.addItemFlags(ItemFlag.values());
    }

    private static String flatten(Component component) {
        return PLAIN_TEXT.serialize(component);
    }

    private record VisualKey(MenuIcon icon, Component title, List<Component> lore, boolean glow, int amount) {

        private VisualKey {
            icon = java.util.Objects.requireNonNull(icon, "icon");
            title = java.util.Objects.requireNonNull(title, "title");
            lore = List.copyOf(lore);
        }

        static VisualKey from(MenuSlot slot) {
            return new VisualKey(slot.icon(), slot.title(), slot.lore(), slot.glow(), slot.amount());
        }
    }
}
