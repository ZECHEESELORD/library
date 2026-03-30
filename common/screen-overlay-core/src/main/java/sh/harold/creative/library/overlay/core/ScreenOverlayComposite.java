package sh.harold.creative.library.overlay.core;

public record ScreenOverlayComposite(boolean visible, int rgb, float opacity) {

    private static final ScreenOverlayComposite HIDDEN = new ScreenOverlayComposite(false, 0, 0.0f);

    public ScreenOverlayComposite {
        if (opacity < 0.0f || opacity > 1.0f) {
            throw new IllegalArgumentException("opacity must be between 0.0 and 1.0");
        }
    }

    public static ScreenOverlayComposite hidden() {
        return HIDDEN;
    }

    public int alphaByte() {
        return Math.round(opacity * 255.0f);
    }

    public int argb() {
        return (alphaByte() << 24) | (rgb & 0x00FFFFFF);
    }
}
