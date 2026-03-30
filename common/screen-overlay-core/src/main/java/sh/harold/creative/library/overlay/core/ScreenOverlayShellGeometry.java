package sh.harold.creative.library.overlay.core;

import org.joml.Matrix4f;

import java.util.List;

public final class ScreenOverlayShellGeometry {
    public static final float PLAYER_WIDTH = 0.6f;
    public static final float PLAYER_HEIGHT = 1.8f;
    public static final float FLOOR_OFFSET = 0.1f;
    public static final float SIDE_CLEARANCE = 2.5f;
    public static final float TOP_CLEARANCE = 1.5f;
    public static final float BACKGROUND_TRANSLATE_X = 0.4f;
    public static final float BACKGROUND_SCALE_X = 8.0f;
    public static final float BACKGROUND_SCALE_Y = 4.0f;
    public static final float BACKGROUND_SCALE_Z = 1.0f;
    public static final float HALF_EXTENT = (PLAYER_WIDTH * 0.5f) + SIDE_CLEARANCE;
    public static final float PRISM_WIDTH = HALF_EXTENT * 2.0f;
    public static final float CEILING_Y = PLAYER_HEIGHT + TOP_CLEARANCE;
    public static final float PRISM_HEIGHT = CEILING_Y - FLOOR_OFFSET;
    public static final float WALL_CENTER_Y = FLOOR_OFFSET + (PRISM_HEIGHT * 0.5f);

    private static final float HALF_PI = (float) (Math.PI / 2.0);
    private static final float PI = (float) Math.PI;
    private static final float THREE_HALVES_PI = (float) (Math.PI * 1.5);
    private static final float BACKGROUND_CENTER_X = BACKGROUND_TRANSLATE_X + (BACKGROUND_SCALE_X * 0.5f);
    private static final float BACKGROUND_CENTER_Y = BACKGROUND_SCALE_Y * 0.5f;
    private static final List<OverlayFace> FACES = List.of(
            OverlayFace.FRONT,
            OverlayFace.RIGHT,
            OverlayFace.BACK,
            OverlayFace.LEFT,
            OverlayFace.UP,
            OverlayFace.DOWN
    );

    private ScreenOverlayShellGeometry() {
    }

    public static List<OverlayFace> faces() {
        return FACES;
    }

    public static Matrix4f faceTransform(OverlayFace face) {
        FaceLayout layout = layout(face);
        return new Matrix4f()
                .translate(layout.centerX(), layout.centerY(), layout.centerZ())
                .mul(rotation(face))
                .scale(layout.width() / BACKGROUND_SCALE_X, layout.height() / BACKGROUND_SCALE_Y, 1.0f)
                .mul(centeredTextBackgroundTransform());
    }

    public static float faceWidth(OverlayFace face) {
        return layout(face).width();
    }

    public static float faceHeight(OverlayFace face) {
        return layout(face).height();
    }

    private static Matrix4f textBackgroundTransform() {
        return new Matrix4f()
                .translate(BACKGROUND_TRANSLATE_X, 0.0f, 0.0f)
                .scale(BACKGROUND_SCALE_X, BACKGROUND_SCALE_Y, BACKGROUND_SCALE_Z);
    }

    private static Matrix4f centeredTextBackgroundTransform() {
        return new Matrix4f()
                .translate(-BACKGROUND_CENTER_X, -BACKGROUND_CENTER_Y, 0.0f)
                .mul(textBackgroundTransform());
    }

    private static FaceLayout layout(OverlayFace face) {
        return switch (face) {
            case FRONT -> new FaceLayout(0.0f, WALL_CENTER_Y, -HALF_EXTENT, PRISM_WIDTH, PRISM_HEIGHT);
            case RIGHT -> new FaceLayout(HALF_EXTENT, WALL_CENTER_Y, 0.0f, PRISM_WIDTH, PRISM_HEIGHT);
            case BACK -> new FaceLayout(0.0f, WALL_CENTER_Y, HALF_EXTENT, PRISM_WIDTH, PRISM_HEIGHT);
            case LEFT -> new FaceLayout(-HALF_EXTENT, WALL_CENTER_Y, 0.0f, PRISM_WIDTH, PRISM_HEIGHT);
            case UP -> new FaceLayout(0.0f, CEILING_Y, 0.0f, PRISM_WIDTH, PRISM_WIDTH);
            case DOWN -> new FaceLayout(0.0f, FLOOR_OFFSET, 0.0f, PRISM_WIDTH, PRISM_WIDTH);
        };
    }

    private static Matrix4f rotation(OverlayFace face) {
        return switch (face) {
            case FRONT -> new Matrix4f();
            case RIGHT -> new Matrix4f().rotationY(HALF_PI);
            case BACK -> new Matrix4f().rotationY(PI);
            case LEFT -> new Matrix4f().rotationY(THREE_HALVES_PI);
            case UP -> new Matrix4f().rotationX(HALF_PI);
            case DOWN -> new Matrix4f().rotationX(-HALF_PI);
        };
    }

    private record FaceLayout(float centerX, float centerY, float centerZ, float width, float height) {
    }
}
