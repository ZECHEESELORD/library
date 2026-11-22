package sh.harold.library.camera.fabric.client;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.harold.library.camera.rig.IntensityProfile;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** The camera's client option, Full or Reduced, in {@code config/creative_library_camera.properties}. */
final class CameraOptions {

    private static final Logger LOGGER = LoggerFactory.getLogger("creative_library_camera");
    private static final String FILE = "creative_library_camera.properties";
    private static final String INTENSITY = "intensity";
    private static volatile IntensityProfile intensity;

    private CameraOptions() {
    }

    static IntensityProfile intensity() {
        IntensityProfile current = intensity;
        if (current == null) {
            current = load();
            intensity = current;
        }
        return current;
    }

    static synchronized void setIntensity(IntensityProfile profile) {
        intensity = profile;
        Properties properties = new Properties();
        properties.setProperty(INTENSITY, profile.name().toLowerCase(Locale.ROOT));
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                properties.store(writer, "Creative Library camera: intensity is full or reduced");
            }
        } catch (IOException e) {
            LOGGER.warn("Could not save {}: {}", file, e.toString());
        }
    }

    private static IntensityProfile load() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return IntensityProfile.FULL;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
            String value = properties.getProperty(INTENSITY, "full").trim().toUpperCase(Locale.ROOT);
            return IntensityProfile.valueOf(value);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Could not read {}, using full intensity: {}", file, e.toString());
            return IntensityProfile.FULL;
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE);
    }
}
