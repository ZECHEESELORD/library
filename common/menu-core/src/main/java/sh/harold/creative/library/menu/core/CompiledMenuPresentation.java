package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuInteraction;
import sh.harold.creative.library.menu.MenuSlot;
import sh.harold.creative.library.menu.MenuTooltipBehavior;

import java.util.List;
import java.util.Map;
import java.util.Objects;

record CompiledMenuPresentation(
        MenuIcon icon,
        Component title,
        List<Component> lore,
        boolean glow,
        int amount,
        MenuTooltipBehavior tooltipBehavior,
        int replaceableLoreLineCount
) {

    CompiledMenuPresentation {
        icon = Objects.requireNonNull(icon, "icon");
        title = Objects.requireNonNull(title, "title");
        lore = List.copyOf(lore);
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        tooltipBehavior = Objects.requireNonNull(tooltipBehavior, "tooltipBehavior");
        if (replaceableLoreLineCount < 0) {
            throw new IllegalArgumentException("replaceableLoreLineCount must be >= 0");
        }
    }

    MenuSlot toMenuSlot(int slot, Map<MenuClick, MenuInteraction> interactions) {
        return new MenuSlot(slot, icon, title, lore, glow, interactions, amount, tooltipBehavior, replaceableLoreLineCount);
    }
}
