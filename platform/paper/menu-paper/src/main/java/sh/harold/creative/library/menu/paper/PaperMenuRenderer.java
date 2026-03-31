package sh.harold.creative.library.menu.paper;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import sh.harold.creative.library.menu.MenuSlot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PaperMenuRenderer implements PaperMenuSlotRenderer {

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
        return cache.computeIfAbsent(slot, this::createItem).clone();
    }

    private ItemStack createItem(MenuSlot slot) {
        ItemStack itemStack = itemFactory.create(slot.icon().key());
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
}
