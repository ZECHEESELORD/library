package sh.harold.creative.library.menu.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.core.MenuTrace;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PaperMenuRenderer implements PaperMenuSlotRenderer {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final PaperMenuItemFactory itemFactory;
    private final Map<MenuSlot, ItemStack> cache = new ConcurrentHashMap<>();

    public PaperMenuRenderer() {
        this(new BukkitPaperMenuItemFactory());
    }

    PaperMenuRenderer(PaperMenuItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    @Override
    public ItemStack render(MenuSlot slot) {
        ItemStack cached = cache.get(slot);
        if (cached == null) {
            long started = System.nanoTime();
            ItemStack created = createItem(slot);
            long elapsed = System.nanoTime() - started;
            ItemStack previous = cache.putIfAbsent(slot, created);
            cached = previous != null ? previous : created;
            if (previous == null) {
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
}
