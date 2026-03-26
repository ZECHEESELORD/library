package sh.harold.creative.library.entity;

import net.kyori.adventure.key.Key;

public final class EntityTypes {

    public static final EntityTypeKey PLAYER_LIKE_HUMANOID = key("creative", "player_like_humanoid", EntityFamily.HUMANOID);
    public static final EntityTypeKey VILLAGER = minecraft("villager", EntityFamily.VILLAGER);
    public static final EntityTypeKey ZOMBIE = minecraft("zombie", EntityFamily.MONSTER);
    public static final EntityTypeKey COW = minecraft("cow", EntityFamily.ANIMAL);
    public static final EntityTypeKey ARMOR_STAND = minecraft("armor_stand", EntityFamily.ARMOR_STAND);
    public static final EntityTypeKey TEXT_DISPLAY = minecraft("text_display", EntityFamily.DISPLAY);
    public static final EntityTypeKey ITEM_DISPLAY = minecraft("item_display", EntityFamily.DISPLAY);
    public static final EntityTypeKey BLOCK_DISPLAY = minecraft("block_display", EntityFamily.DISPLAY);
    public static final EntityTypeKey INTERACTION = minecraft("interaction", EntityFamily.UTILITY);

    private EntityTypes() {
    }

    public static EntityTypeKey minecraft(String value, EntityFamily family) {
        return key(Key.key(Key.MINECRAFT_NAMESPACE, value), family);
    }

    public static EntityTypeKey key(String namespace, String value, EntityFamily family) {
        return key(Key.key(namespace, value), family);
    }

    public static EntityTypeKey key(Key key, EntityFamily family) {
        return new EntityTypeKey(key, family);
    }
}
