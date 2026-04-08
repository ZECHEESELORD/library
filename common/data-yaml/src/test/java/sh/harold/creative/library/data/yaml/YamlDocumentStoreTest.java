package sh.harold.creative.library.data.yaml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlDocumentStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void ownedExecutorClosesGracefully() throws Exception {
        YamlDocumentStore store = new YamlDocumentStore(tempDir.resolve("yaml-store"));
        ExecutorService executor = ownedExecutor(store);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);

        Future<?> blocker = executor.submit(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });

        assertTrue(started.await(5, TimeUnit.SECONDS));

        CompletableFuture<Void> closeFuture = CompletableFuture.runAsync(store::close);
        Thread.sleep(200L);

        assertFalse(closeFuture.isDone());
        assertFalse(interrupted.get());

        release.countDown();
        closeFuture.get(5, TimeUnit.SECONDS);
        assertTrue(blocker.isDone());
    }

    private static ExecutorService ownedExecutor(YamlDocumentStore store) throws Exception {
        Field field = YamlDocumentStore.class.getDeclaredField("ownedExecutor");
        field.setAccessible(true);
        return (ExecutorService) field.get(store);
    }
}
