package sh.harold.library.tween.track;

/** How a {@link TrackLayer} combines with the value beneath it. */
public enum BlendMode {
    /** {@code lerp(below, layer, weight)}. */
    OVERRIDE,
    /** {@code below + weight * layer}; for rotations, the layer's rotation scaled by weight is applied locally. */
    ADDITIVE
}
