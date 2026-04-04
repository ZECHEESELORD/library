package sh.harold.creative.library.menu;

import java.util.Locale;
import java.util.Objects;

public record MenuIcon(String key, String textureValue) {

    public MenuIcon {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key cannot be blank");
        }
        key = normalize(key);
        if (textureValue != null) {
            textureValue = textureValue.trim();
            if (textureValue.isBlank()) {
                throw new IllegalArgumentException("textureValue cannot be blank");
            }
            key = normalize("player_head");
        }
    }

    public static MenuIcon vanilla(String key) {
        return new MenuIcon(key, null);
    }

    public static MenuIcon customHead(String textureValue) {
        return new MenuIcon("player_head", Objects.requireNonNull(textureValue, "textureValue"));
    }

    public boolean isCustomHead() {
        return textureValue != null;
    }

    private static String normalize(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }
}
