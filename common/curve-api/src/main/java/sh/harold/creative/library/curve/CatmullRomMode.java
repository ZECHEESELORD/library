package sh.harold.creative.library.curve;

public enum CatmullRomMode {
    CENTRIPETAL(0.5),
    CHORDAL(1.0);

    private final double alpha;

    CatmullRomMode(double alpha) {
        this.alpha = alpha;
    }

    public double alpha() {
        return alpha;
    }
}
