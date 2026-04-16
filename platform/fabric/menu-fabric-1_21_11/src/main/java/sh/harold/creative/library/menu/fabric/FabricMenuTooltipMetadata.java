package sh.harold.creative.library.menu.fabric;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuTooltipBehavior;

public final class FabricMenuTooltipMetadata {

    private static final String ROOT_KEY = "creative_menu";
    private static final String KEY_BEHAVIOR = "tooltip_behavior";
    private static final String KEY_REPLACEABLE_LORE_LINES = "replaceable_lore_lines";

    private FabricMenuTooltipMetadata() {
    }

    static void apply(ItemStack stack, MenuSlot slot) {
        if (stack == null || slot == null) {
            return;
        }
        CompoundTag custom = currentCustomData(stack);
        CompoundTag root = custom.contains(ROOT_KEY)
                ? custom.getCompound(ROOT_KEY).map(CompoundTag::copy).orElseGet(CompoundTag::new)
                : new CompoundTag();
        root.putString(KEY_BEHAVIOR, slot.tooltipBehavior().name());
        if (slot.replaceableLoreLineCount() > 0) {
            root.putInt(KEY_REPLACEABLE_LORE_LINES, slot.replaceableLoreLineCount());
        } else {
            root.remove(KEY_REPLACEABLE_LORE_LINES);
        }
        custom.put(ROOT_KEY, root);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }

    static void clear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return;
        }
        CompoundTag custom = customData.copyTag();
        if (!custom.contains(ROOT_KEY)) {
            return;
        }
        custom.remove(ROOT_KEY);
        if (custom.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }

    public static boolean hasMenuMetadata(ItemStack stack) {
        return behavior(stack) != null;
    }

    public static boolean isChrome(ItemStack stack) {
        return behavior(stack) == MenuTooltipBehavior.CHROME;
    }

    public static boolean isLiteral(ItemStack stack) {
        return behavior(stack) == MenuTooltipBehavior.LITERAL;
    }

    public static int replaceableLoreLineCount(ItemStack stack, int fallback) {
        int safeFallback = Math.max(0, fallback);
        if (!isLiteral(stack)) {
            return safeFallback;
        }
        CompoundTag root = rootTag(stack);
        if (root == null || !root.contains(KEY_REPLACEABLE_LORE_LINES)) {
            return safeFallback;
        }
        int value = root.getInt(KEY_REPLACEABLE_LORE_LINES).orElse(safeFallback);
        return Math.min(Math.max(0, value), safeFallback);
    }

    private static MenuTooltipBehavior behavior(ItemStack stack) {
        CompoundTag root = rootTag(stack);
        if (root == null || !root.contains(KEY_BEHAVIOR)) {
            return null;
        }
        String raw = root.getString(KEY_BEHAVIOR).orElse("");
        try {
            return MenuTooltipBehavior.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static CompoundTag rootTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        CompoundTag custom = customData.copyTag();
        if (!custom.contains(ROOT_KEY)) {
            return null;
        }
        return custom.getCompound(ROOT_KEY).orElse(null);
    }

    private static CompoundTag currentCustomData(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null ? new CompoundTag() : customData.copyTag();
    }
}
