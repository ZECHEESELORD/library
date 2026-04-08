package sh.harold.creative.library.data.minestom;

import sh.harold.creative.library.data.SharedDataAccess;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinestomSharedDataBridgeTest {

    @Test
    void registryIsVisibleAcrossClassLoaders() throws Exception {
        String ownerId = "owner-" + UUID.randomUUID().toString().replace("-", "");
        URL mainClasses = MinestomSharedDataBridge.class.getProtectionDomain().getCodeSource().getLocation();
        ClassLoader parent = MinestomSharedDataBridgeTest.class.getClassLoader();

        try (ChildFirstLoader loaderA = new ChildFirstLoader(new URL[]{mainClasses}, parent);
             ChildFirstLoader loaderB = new ChildFirstLoader(new URL[]{mainClasses}, parent)) {
            Class<?> bridgeA = Class.forName(MinestomSharedDataBridge.class.getName(), true, loaderA);
            Class<?> bridgeB = Class.forName(MinestomSharedDataBridge.class.getName(), true, loaderB);
            TestEndpoint endpoint = new TestEndpoint();

            invokeStatic(bridgeA, "register", new Class<?>[]{String.class, Object.class}, ownerId, endpoint);

            SharedDataAccess access = (SharedDataAccess) invokeStatic(
                    bridgeB,
                    "connect",
                    new Class<?>[]{String.class, String.class},
                    "caller-a",
                    ownerId
            );
            assertEquals("owner-default", access.defaultNamespace().name());

            invokeStatic(bridgeA, "unregister", new Class<?>[]{String.class, Object.class}, ownerId, endpoint);

            InvocationTargetException exception = assertThrows(InvocationTargetException.class, () ->
                    invokeStatic(bridgeB, "connect", new Class<?>[]{String.class, String.class}, "caller-a", ownerId));
            assertEquals(IllegalStateException.class, exception.getCause().getClass());
        }
    }

    private static Object invokeStatic(Class<?> type, String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = type.getMethod(name, parameterTypes);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException exception) {
            throw exception;
        }
    }

    private static final class ChildFirstLoader extends URLClassLoader {

        private final String childFirstClassName = MinestomSharedDataBridge.class.getName();

        private ChildFirstLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(childFirstClassName)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        loaded = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    public static final class TestEndpoint {

        public String defaultNamespace(String callerId) {
            return "owner-default";
        }

        public boolean canAccessNamespace(String callerId, String namespace) {
            return true;
        }

        public CompletionStage<Long> count(String callerId, String namespace, String collection) {
            return CompletableFuture.completedFuture(0L);
        }

        public CompletionStage<List<String>> listIds(String callerId, String namespace, String collection) {
            return CompletableFuture.completedFuture(List.of());
        }

        public CompletionStage<Map<String, Object>> read(String callerId, String namespace, String collection, String id) {
            return CompletableFuture.completedFuture(Map.of());
        }

        public CompletionStage<Map<String, Object>> write(String callerId,
                                                          String namespace,
                                                          String collection,
                                                          String id,
                                                          Map<String, Object> data,
                                                          Map<String, Object> conditionPayload) {
            return CompletableFuture.completedFuture(Map.of("status", "APPLIED", "snapshot", null));
        }

        public CompletionStage<Map<String, Object>> patch(String callerId,
                                                          String namespace,
                                                          String collection,
                                                          String id,
                                                          Map<String, Object> patchPayload,
                                                          Map<String, Object> conditionPayload) {
            return CompletableFuture.completedFuture(Map.of("status", "APPLIED", "snapshot", null));
        }

        public CompletionStage<Map<String, Object>> delete(String callerId,
                                                           String namespace,
                                                           String collection,
                                                           String id,
                                                           Map<String, Object> conditionPayload) {
            return CompletableFuture.completedFuture(Map.of("status", "APPLIED", "snapshot", null));
        }
    }
}
