package sh.harold.creative.library.menu;

import java.util.Locale;
import java.util.Objects;

public record MenuIcon(String key) {

    public MenuIcon {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key cannot be blank");
        }
        key = normalize(key);
    }

    public static MenuIcon vanilla(String key) {
        return new MenuIcon(key);
    }

    private static String normalize(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }
}
