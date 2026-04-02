package sh.harold.creative.library.menu.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class PaperMenuTestSupport {

    private PaperMenuTestSupport() {
    }

    static Player player(UUID uuid) {
        AtomicReference<ItemStack> cursor = new AtomicReference<>();
        InventoryState inventoryState = new InventoryState(36);
        Player player = proxy(Player.class, new PlayerHandler(uuid, inventoryState, cursor));
        inventoryState.holder = player;
        return player;
    }

    static Inventory inventory(InventoryHolder holder, int size) {
        InventoryState state = new InventoryState(size);
        state.holder = holder;
        return state.as(Inventory.class);
    }

    static ItemStack item(Material material, int amount, Component name, List<Component> lore, boolean glow) {
        return new FakeItemStack(material, amount, new MetaState(name, lore, glow ? Boolean.TRUE : null));
    }

    static ItemStack namedItem(Material material, String name, int amount) {
        return item(material, amount, Component.text(name), null, false);
    }

    static ItemStack renderedItem(String iconKey, int amount, Component title, List<Component> lore, boolean glow) {
        return item(material(iconKey), amount, title, lore, glow);
    }

    static Material material(String iconKey) {
        String resolved = iconKey.startsWith("minecraft:") ? iconKey.substring("minecraft:".length()) : iconKey;
        Material material = Material.matchMaterial(resolved);
        if (material == null) {
            throw new IllegalArgumentException("Unknown test material for key: " + iconKey);
        }
        return material;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static final class PlayerHandler implements InvocationHandler {

        private final UUID uuid;
        private final InventoryState inventoryState;
        private final AtomicReference<ItemStack> cursor;
        private PlayerInventory inventory;

        private PlayerHandler(UUID uuid, InventoryState inventoryState, AtomicReference<ItemStack> cursor) {
            this.uuid = Objects.requireNonNull(uuid, "uuid");
            this.inventoryState = Objects.requireNonNull(inventoryState, "inventoryState");
            this.cursor = Objects.requireNonNull(cursor, "cursor");
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if (name.equals("getUniqueId")) {
                return uuid;
            }
            if (name.equals("getInventory")) {
                if (inventory == null) {
                    inventory = inventoryState.as(PlayerInventory.class);
                }
                return inventory;
            }
            if (name.equals("setItemOnCursor")) {
                cursor.set(args == null ? null : (ItemStack) args[0]);
                return null;
            }
            if (name.equals("getItemOnCursor")) {
                return cursor.get();
            }
            if (name.equals("isOnline")) {
                return true;
            }
            if (name.equals("equals")) {
                return proxy == args[0];
            }
            if (name.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (name.equals("toString")) {
                return "FakePlayer[" + uuid + "]";
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static final class InventoryState implements InvocationHandler {

        private final int size;
        private final Map<Integer, ItemStack> items = new HashMap<>();
        private InventoryHolder holder;

        private InventoryState(int size) {
            this.size = size;
        }

        private <T> T as(Class<T> type) {
            return proxy(type, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if (name.equals("getSize")) {
                return size;
            }
            if (name.equals("getType")) {
                return null;
            }
            if (name.equals("setItem")) {
                int slot = (Integer) args[0];
                ItemStack item = (ItemStack) args[1];
                if (item == null || item.getType() == null || item.getType() == Material.AIR) {
                    items.remove(slot);
                } else {
                    items.put(slot, item);
                }
                return null;
            }
            if (name.equals("getItem")) {
                return items.get((Integer) args[0]);
            }
            if (name.equals("clear")) {
                if (args == null || args.length == 0) {
                    items.clear();
                } else {
                    items.remove((Integer) args[0]);
                }
                return null;
            }
            if (name.equals("getHolder")) {
                return holder;
            }
            if (name.equals("getContents") || name.equals("getStorageContents")) {
                ItemStack[] contents = new ItemStack[size];
                items.forEach((slot, item) -> {
                    if (slot >= 0 && slot < size) {
                        contents[slot] = item;
                    }
                });
                return contents;
            }
            if (name.equals("setContents") || name.equals("setStorageContents")) {
                items.clear();
                ItemStack[] contents = (ItemStack[]) args[0];
                for (int slot = 0; slot < contents.length && slot < size; slot++) {
                    if (contents[slot] != null) {
                        items.put(slot, contents[slot]);
                    }
                }
                return null;
            }
            if (name.equals("equals")) {
                return proxy == args[0];
            }
            if (name.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (name.equals("toString")) {
                return "FakeInventory[size=" + size + "]";
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static final class FakeItemStack extends ItemStack {

        private Material type;
        private int amount;
        private MetaState meta;

        private FakeItemStack(Material type, int amount, MetaState meta) {
            this.type = Objects.requireNonNull(type, "type");
            this.amount = amount;
            this.meta = meta.copy();
        }

        @Override
        public Material getType() {
            return type;
        }

        @Override
        public void setType(Material type) {
            this.type = Objects.requireNonNull(type, "type");
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public void setAmount(int amount) {
            this.amount = amount;
        }

        @Override
        public ItemMeta getItemMeta() {
            return meta.proxy();
        }

        @Override
        public boolean hasItemMeta() {
            return meta.hasData();
        }

        @Override
        public boolean setItemMeta(ItemMeta itemMeta) {
            this.meta = MetaState.copyOf(itemMeta);
            return true;
        }

        @Override
        public boolean editMeta(Consumer<? super ItemMeta> consumer) {
            ItemMeta working = MetaState.copyOf(meta.proxy()).proxy();
            consumer.accept(working);
            return setItemMeta(working);
        }

        @Override
        public FakeItemStack clone() {
            return new FakeItemStack(type, amount, meta.copy());
        }
    }

    private static final class MetaState implements InvocationHandler {

        private Component displayName;
        private List<Component> lore;
        private Boolean glint;
        private final Set<ItemFlag> itemFlags;

        private MetaState(Component displayName, List<Component> lore, Boolean glint) {
            this(displayName, lore == null ? null : new ArrayList<>(lore), glint, EnumSet.noneOf(ItemFlag.class));
        }

        private MetaState(Component displayName, List<Component> lore, Boolean glint, Set<ItemFlag> itemFlags) {
            this.displayName = displayName;
            this.lore = lore;
            this.glint = glint;
            this.itemFlags = itemFlags;
        }

        private static MetaState copyOf(ItemMeta itemMeta) {
            if (itemMeta == null) {
                return new MetaState(null, null, null);
            }
            if (Proxy.isProxyClass(itemMeta.getClass())) {
                InvocationHandler handler = Proxy.getInvocationHandler(itemMeta);
                if (handler instanceof MetaState state) {
                    return state.copy();
                }
            }
            Set<ItemFlag> flags = itemMeta.getItemFlags();
            return new MetaState(itemMeta.displayName(), itemMeta.lore(), itemMeta.getEnchantmentGlintOverride(),
                    copyFlags(flags));
        }

        private MetaState copy() {
            return new MetaState(displayName, lore == null ? null : new ArrayList<>(lore), glint, copyFlags(itemFlags));
        }

        private ItemMeta proxy() {
            return PaperMenuTestSupport.proxy(ItemMeta.class, this);
        }

        private boolean hasData() {
            return displayName != null || lore != null || glint != null || !itemFlags.isEmpty();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if (name.equals("displayName")) {
                if (args == null || args.length == 0) {
                    return displayName;
                }
                displayName = (Component) args[0];
                return null;
            }
            if (name.equals("lore")) {
                if (args == null || args.length == 0) {
                    return lore == null ? null : List.copyOf(lore);
                }
                @SuppressWarnings("unchecked")
                List<Component> nextLore = (List<Component>) args[0];
                lore = nextLore == null ? null : new ArrayList<>(nextLore);
                return null;
            }
            if (name.equals("getEnchantmentGlintOverride")) {
                return glint;
            }
            if (name.equals("setEnchantmentGlintOverride")) {
                glint = (Boolean) args[0];
                return null;
            }
            if (name.equals("addItemFlags")) {
                for (ItemFlag flag : (ItemFlag[]) args[0]) {
                    itemFlags.add(flag);
                }
                return null;
            }
            if (name.equals("getItemFlags")) {
                return Set.copyOf(itemFlags);
            }
            if (name.equals("hasItemFlag")) {
                return itemFlags.contains((ItemFlag) args[0]);
            }
            if (name.equals("clone")) {
                return copy().proxy();
            }
            if (name.equals("equals")) {
                return proxy == args[0];
            }
            if (name.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (name.equals("toString")) {
                return "FakeItemMeta";
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == double.class) {
            return 0.0d;
        }
        return null;
    }

    private static EnumSet<ItemFlag> copyFlags(Set<ItemFlag> flags) {
        return flags.isEmpty() ? EnumSet.noneOf(ItemFlag.class) : EnumSet.copyOf(flags);
    }
}
