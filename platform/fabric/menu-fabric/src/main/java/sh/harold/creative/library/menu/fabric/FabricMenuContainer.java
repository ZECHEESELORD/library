package sh.harold.creative.library.menu.fabric;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import sh.harold.creative.library.menu.MenuClick;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

final class FabricMenuContainer extends ChestMenu {

    private final FabricMenuRuntime runtime;
    private final FabricMenuSession session;
    private final SimpleContainer topContainer;
    private final int rows;
    private final Set<Integer> dragSlots = new LinkedHashSet<>();
    private volatile boolean transitionClosing;
    private volatile boolean promptClosing;
    private volatile boolean closed;
    private volatile MenuClick dragButton;

    FabricMenuContainer(FabricMenuRuntime runtime, FabricMenuSession session, int containerId, Inventory playerInventory,
                        SimpleContainer topContainer, int rows) {
        super(menuType(rows), containerId, playerInventory, topContainer, rows);
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.session = Objects.requireNonNull(session, "session");
        this.topContainer = Objects.requireNonNull(topContainer, "topContainer");
        this.rows = rows;
    }

    FabricMenuSession session() {
        return session;
    }

    SimpleContainer topContainer() {
        return topContainer;
    }

    int rows() {
        return rows;
    }

    boolean closed() {
        return closed;
    }

    boolean transitionClosing() {
        return transitionClosing;
    }

    boolean promptClosing() {
        return promptClosing;
    }

    void markTransitionClose() {
        this.transitionClosing = true;
    }

    void markPromptClose() {
        this.promptClosing = true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack itemStack, Slot slot) {
        return false;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (input == ContainerInput.QUICK_CRAFT) {
            handleQuickCraft(slotId, button, serverPlayer);
            return;
        }
        if (slotId == SLOT_CLICKED_OUTSIDE) {
            MenuClick menuClick = toMenuClick(button);
            if (input == ContainerInput.THROW && menuClick != null) {
                runtime.onDropCursor(this, serverPlayer, menuClick);
            }
            return;
        }
        if (!isValidSlotIndex(slotId)) {
            return;
        }
        ClickBinding click = resolveClick(input, button);
        if (click == null) {
            return;
        }
        Slot slot = getSlot(slotId);
        if (slot.container == topContainer) {
            runtime.onTopClick(this, serverPlayer, slotId, click.button(), click.shift());
            return;
        }
        if (slot.container == player.getInventory()) {
            runtime.onBottomClick(this, serverPlayer, slot.getContainerSlot(), click.button(), click.shift(), slot.getItem());
        }
    }

    @Override
    public void removed(Player player) {
        closed = true;
        boolean softClose = transitionClosing || promptClosing;
        if (!softClose) {
            super.removed(player);
        } else {
            topContainer.stopOpen(player);
            setCarried(ItemStack.EMPTY);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            runtime.onContainerRemoved(this, serverPlayer);
        }
    }

    private void handleQuickCraft(int slotId, int button, ServerPlayer player) {
        int header = getQuickcraftHeader(button);
        MenuClick menuClick = toMenuClick(getQuickcraftType(button));
        if (menuClick == null) {
            dragSlots.clear();
            dragButton = null;
            return;
        }
        switch (header) {
            case QUICKCRAFT_HEADER_START -> {
                dragSlots.clear();
                dragButton = menuClick;
            }
            case QUICKCRAFT_HEADER_CONTINUE -> {
                if (dragButton != menuClick || slotId < 0 || slotId >= topContainer.getContainerSize()) {
                    return;
                }
                dragSlots.add(slotId);
            }
            case QUICKCRAFT_HEADER_END -> {
                if (dragButton != menuClick || dragSlots.isEmpty()) {
                    dragSlots.clear();
                    dragButton = null;
                    return;
                }
                runtime.onDrag(this, player, menuClick, dragSlots.stream().sorted().toList());
                dragSlots.clear();
                dragButton = null;
            }
            default -> {
            }
        }
    }

    private static MenuType<?> menuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            case 6 -> MenuType.GENERIC_9x6;
            default -> throw new IllegalArgumentException("Unsupported menu row count: " + rows);
        };
    }

    static ClickBinding resolveClick(ContainerInput input, int button) {
        Objects.requireNonNull(input, "input");
        return switch (input) {
            case PICKUP -> switch (button) {
                case 0 -> new ClickBinding(MenuClick.LEFT, false);
                case 1 -> new ClickBinding(MenuClick.RIGHT, false);
                default -> null;
            };
            case QUICK_MOVE -> switch (button) {
                case 0 -> new ClickBinding(MenuClick.LEFT, true);
                case 1 -> new ClickBinding(MenuClick.RIGHT, true);
                default -> null;
            };
            default -> null;
        };
    }

    private static MenuClick toMenuClick(int button) {
        return switch (button) {
            case 0 -> MenuClick.LEFT;
            case 1 -> MenuClick.RIGHT;
            default -> null;
        };
    }

    record ClickBinding(MenuClick button, boolean shift) {
    }
}
