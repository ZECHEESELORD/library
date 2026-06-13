package sh.harold.creative.library.menu.paper;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import sh.harold.creative.library.menu.MenuIcon;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

final class PaperMenuIcons {

    private static final String TEXTURES_PROPERTY = "textures";

    private PaperMenuIcons() {
    }

    static ItemStack createItem(MenuIcon icon) {
        Objects.requireNonNull(icon, "icon");
        Material material = resolveMaterial(icon.key());
        ItemStack itemStack = new ItemStack(material);
        if (!icon.isCustomHead()) {
            return itemStack;
        }
        itemStack.editMeta(meta -> applyHeadTexture(meta, icon.textureValue()));
        return itemStack;
    }

    static MenuIcon fromItemStack(ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack");
        String textureValue = textureValue(itemStack);
        if (textureValue != null) {
            return MenuIcon.customHead(textureValue);
        }
        return MenuIcon.vanilla(itemStack.getType().getKey().asString());
    }

    private static void applyHeadTexture(ItemMeta meta, String textureValue) {
        if (!(meta instanceof SkullMeta skullMeta)) {
            throw new IllegalArgumentException("Custom head menu icons require PLAYER_HEAD metadata.");
        }
        PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(textureValue.getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new ProfileProperty(TEXTURES_PROPERTY, textureValue));
        skullMeta.setPlayerProfile(profile);
    }

    private static String textureValue(ItemStack itemStack) {
        if (itemStack.getType() != Material.PLAYER_HEAD) {
            return null;
        }
        if (!(itemStack.getItemMeta() instanceof SkullMeta skullMeta)) {
            return null;
        }
        PlayerProfile profile = skullMeta.getPlayerProfile();
        if (profile == null) {
            return null;
        }
        return profile.getProperties().stream()
                .filter(property -> TEXTURES_PROPERTY.equals(property.getName()))
                .map(ProfileProperty::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static Material resolveMaterial(String key) {
        Material material = Material.matchMaterial(key);
        if (material == null) {
            throw new IllegalArgumentException("Unknown Paper material for menu icon: " + key);
        }
        return material;
    }
}
