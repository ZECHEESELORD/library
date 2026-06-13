package sh.harold.creative.library.data.paper;

import org.bukkit.plugin.Plugin;
import sh.harold.creative.library.data.SharedDataAccess;
import sh.harold.creative.library.data.core.ReflectiveSharedDataClient;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

public final class PaperSharedDataBridge {

    private PaperSharedDataBridge() {
    }

    public static SharedDataAccess connect(Plugin caller, String ownerPluginName) {
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(ownerPluginName, "ownerPluginName");
        Plugin owner = caller.getServer().getPluginManager().getPlugin(ownerPluginName);
        if (owner == null) {
            throw new IllegalStateException("shared data owner plugin not installed: " + ownerPluginName);
        }
        if (!owner.isEnabled()) {
            throw new IllegalStateException("shared data owner plugin is not enabled: " + ownerPluginName);
        }
        return ReflectiveSharedDataClient.connect(sharedDataBridge(owner), caller.getName());
    }

    private static Object sharedDataBridge(Plugin owner) {
        try {
            Method method = owner.getClass().getMethod("sharedDataBridge");
            return method.invoke(owner);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("owner plugin does not expose sharedDataBridge()", exception);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("failed to access sharedDataBridge() on owner plugin", exception);
        }
    }
}
