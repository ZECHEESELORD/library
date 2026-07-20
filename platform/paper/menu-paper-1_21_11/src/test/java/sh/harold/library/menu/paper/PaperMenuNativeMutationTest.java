package sh.harold.library.menu.paper;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class PaperMenuNativeMutationTest {

    @Test
    void rollbackStopsBeforeRestoringSourceWhenDestinationUndoFails() throws Exception {
        Player player = mock(Player.class);
        PlayerInventory viewerInventory = mock(PlayerInventory.class);
        Inventory topInventory = mock(Inventory.class);
        ItemStack source = PaperMenuTestSupport.namedItem(Material.EMERALD, "Source", 3);
        ItemStack base = PaperMenuTestSupport.namedItem(Material.STONE_BUTTON, "Base", 1);
        ItemStack destination = source.clone();

        when(player.getInventory()).thenReturn(viewerInventory);
        when(viewerInventory.getItem(5)).thenReturn(source);
        when(topInventory.getItem(31)).thenReturn(base);
        AtomicInteger topWrites = new AtomicInteger();
        doAnswer(invocation -> {
            if (topWrites.incrementAndGet() == 2) {
                throw new IllegalStateException("destination undo failed");
            }
            return null;
        }).when(topInventory).setItem(eq(31), any(ItemStack.class));

        Object mutation = nativeMutation(player, topInventory);
        invoke(mutation, "setViewerSlot", new Class<?>[]{int.class, ItemStack.class}, 5, null);
        invoke(mutation, "setTopSlot", new Class<?>[]{int.class, ItemStack.class}, 31, destination);
        assertFalse((Boolean) invoke(mutation, "rollback", new Class<?>[0]));

        verify(viewerInventory).setItem(eq(5), isNull());
        verify(viewerInventory, never()).setItem(eq(5), eq(source));
        verify(topInventory).setItem(eq(31), eq(destination));
        verify(topInventory).setItem(eq(31), eq(base));
    }

    private static Object nativeMutation(Player player, Inventory topInventory) throws Exception {
        Class<?> type = Class.forName(PaperMenuRuntime.class.getName() + "$NativeMutation");
        Constructor<?> constructor = type.getDeclaredConstructor(Player.class, Inventory.class);
        constructor.setAccessible(true);
        return constructor.newInstance(player, topInventory);
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }
}
