package sh.harold.creative.library.menu.minestom;

import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.ResolvableProfile;
import sh.harold.creative.library.menu.MenuIcon;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class MinestomMenuIcons {

    private static final String TEXTURES_PROPERTY = "textures";

    private MinestomMenuIcons() {
    }

    static ItemStack createItem(MenuIcon icon, int amount) {
        Objects.requireNonNull(icon, "icon");
        Material material = Material.fromKey(icon.key());
        if (material == null) {
            throw new IllegalArgumentException("Unknown Minestom material for menu icon: " + icon.key());
        }
        ItemStack itemStack = ItemStack.of(material, amount);
        if (!icon.isCustomHead()) {
            return itemStack;
        }
        return itemStack.with(DataComponents.PROFILE, profile(icon.textureValue()));
    }

    static MenuIcon fromItemStack(ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack");
        String textureValue = textureValue(itemStack);
        if (textureValue != null) {
            return MenuIcon.customHead(textureValue);
        }
        return MenuIcon.vanilla(itemStack.material().key().asString());
    }

    private static ResolvableProfile profile(String textureValue) {
        UUID profileId = UUID.nameUUIDFromBytes(textureValue.getBytes(StandardCharsets.UTF_8));
        GameProfile gameProfile = new GameProfile(profileId, "", List.of(new GameProfile.Property(TEXTURES_PROPERTY, textureValue)));
        return new ResolvableProfile(gameProfile);
    }

    private static String textureValue(ItemStack itemStack) {
        if (itemStack.material() != Material.PLAYER_HEAD) {
            return null;
        }
        ResolvableProfile profile = itemStack.get(DataComponents.PROFILE);
        if (profile == null) {
            return null;
        }
        List<GameProfile.Property> properties = profile.profile().unify(GameProfile::properties, ResolvableProfile.Partial::properties);
        return properties.stream()
                .filter(property -> TEXTURES_PROPERTY.equals(property.name()))
                .map(GameProfile.Property::value)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
