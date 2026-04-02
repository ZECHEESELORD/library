package sh.harold.creative.library.data.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import sh.harold.creative.library.data.SharedDataAccess;
import sh.harold.creative.library.data.core.ReflectiveSharedDataClient;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

public final class VelocitySharedDataBridge {

    private VelocitySharedDataBridge() {
    }

    public static SharedDataAccess connect(ProxyServer server, Object callerInstance, String ownerPluginId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(callerInstance, "callerInstance");
        Objects.requireNonNull(ownerPluginId, "ownerPluginId");
        Object ownerInstance = server.getPluginManager()
                .getPlugin(ownerPluginId)
                .orElseThrow(() -> new IllegalStateException("shared data owner plugin not installed: " + ownerPluginId))
                .getInstance()
                .orElseThrow(() -> new IllegalStateException("shared data owner plugin instance unavailable: " + ownerPluginId));
        String callerId = server.getPluginManager()
                .fromInstance(callerInstance)
                .orElseThrow(() -> new IllegalStateException("caller plugin is not registered with Velocity"))
                .getDescription()
                .getId();
        return ReflectiveSharedDataClient.connect(sharedDataBridge(ownerInstance), callerId);
    }

    private static Object sharedDataBridge(Object ownerInstance) {
        try {
            Method method = ownerInstance.getClass().getMethod("sharedDataBridge");
            return method.invoke(ownerInstance);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("owner plugin does not expose sharedDataBridge()", exception);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("failed to access sharedDataBridge() on owner plugin", exception);
        }
    }
}
