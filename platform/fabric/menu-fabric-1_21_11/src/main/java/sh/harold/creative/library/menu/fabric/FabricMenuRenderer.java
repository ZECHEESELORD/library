package sh.harold.creative.library.menu.fabric;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuTooltipBehavior;
import sh.harold.creative.library.menu.core.MenuTrace;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FabricMenuRenderer {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final int CACHE_LIMIT = 4_096;

    private final Map<VisualKey, ItemStack> cache = new LinkedHashMap<>(CACHE_LIMIT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<VisualKey, ItemStack> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    ItemStack render(MenuSlot slot, HolderLookup.Provider registries) {
        VisualKey key = VisualKey.from(slot);
        ItemStack cached;
        synchronized (cache) {
            cached = cache.get(key);
        }
        if (cached == null) {
            long started = System.nanoTime();
            ItemStack created = createItem(slot, registries);
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
        return cached.copy();
    }

    private static ItemStack createItem(MenuSlot slot, HolderLookup.Provider registries) {
        ItemStack itemStack = FabricMenuIcons.createItem(slot.icon(), slot.amount());
        itemStack.set(DataComponents.CUSTOM_NAME, FabricMenuComponents.toNative(slot.title(), registries));
        if (slot.lore().isEmpty()) {
            itemStack.remove(DataComponents.LORE);
        } else {
            itemStack.set(DataComponents.LORE, new ItemLore(slot.lore().stream()
                    .map(line -> FabricMenuComponents.toNative(line, registries))
                    .toList()));
        }
        itemStack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, slot.glow() ? Boolean.TRUE : null);
        FabricMenuTooltipMetadata.apply(itemStack, slot);
        return itemStack;
    }

    private static String flatten(Component component) {
        return PLAIN_TEXT.serialize(component);
    }

    private record VisualKey(
            MenuIcon icon,
            Component title,
            List<Component> lore,
            boolean glow,
            int amount,
            MenuTooltipBehavior tooltipBehavior,
            int replaceableLoreLineCount
    ) {

        private VisualKey {
            icon = java.util.Objects.requireNonNull(icon, "icon");
            title = java.util.Objects.requireNonNull(title, "title");
            lore = List.copyOf(lore);
        }

        static VisualKey from(MenuSlot slot) {
            return new VisualKey(slot.icon(), slot.title(), slot.lore(), slot.glow(), slot.amount(),
                    slot.tooltipBehavior(), slot.replaceableLoreLineCount());
        }
    }
}
