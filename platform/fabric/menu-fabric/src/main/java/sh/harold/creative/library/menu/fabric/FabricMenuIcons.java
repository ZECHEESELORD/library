package sh.harold.creative.library.menu.fabric;

import com.google.common.collect.LinkedHashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import sh.harold.creative.library.menu.MenuIcon;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

final class FabricMenuIcons {

    private static final String TEXTURES_PROPERTY = "textures";

    private FabricMenuIcons() {
    }

    static ItemStack createItem(MenuIcon icon, int amount) {
        Objects.requireNonNull(icon, "icon");
        Item item = resolveItem(icon.key());
        ItemStack itemStack = new ItemStack(item, amount);
        if (!icon.isCustomHead()) {
            return itemStack;
        }
        itemStack.set(DataComponents.PROFILE, profile(icon.textureValue()));
        return itemStack;
    }

    static MenuIcon fromItemStack(ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack");
        String textureValue = textureValue(itemStack);
        if (textureValue != null) {
            return MenuIcon.customHead(textureValue);
        }
        return MenuIcon.vanilla(BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString());
    }

    private static ResolvableProfile profile(String textureValue) {
        var properties = LinkedHashMultimap.<String, Property>create();
        properties.put(TEXTURES_PROPERTY, new Property(TEXTURES_PROPERTY, textureValue));
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(textureValue.getBytes(StandardCharsets.UTF_8)), "", new PropertyMap(properties));
        return ResolvableProfile.createResolved(profile);
    }

    private static String textureValue(ItemStack itemStack) {
        if (itemStack.getItem() != Items.PLAYER_HEAD) {
            return null;
        }
        ResolvableProfile profile = itemStack.get(DataComponents.PROFILE);
        if (profile == null) {
            return null;
        }
        Collection<Property> properties = profile.partialProfile().properties().get(TEXTURES_PROPERTY);
        for (Property property : properties) {
            if (property != null && property.value() != null && !property.value().isBlank()) {
                return property.value();
            }
        }
        return null;
    }

    private static Item resolveItem(String key) {
        Identifier identifier = Identifier.parse(Objects.requireNonNull(key, "key"));
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        if (item == null || (item == Items.AIR && !"minecraft:air".equals(identifier.toString()))) {
            throw new IllegalArgumentException("Unknown Fabric item for menu icon: " + key);
        }
        return item;
    }
}
