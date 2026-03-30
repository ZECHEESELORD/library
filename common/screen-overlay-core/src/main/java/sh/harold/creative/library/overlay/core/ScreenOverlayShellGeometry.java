package sh.harold.creative.library.overlay.core;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public final class ScreenOverlayShellGeometry {
    public static final float PLAYER_WIDTH = 0.6f;
    public static final float PLAYER_HEIGHT = 1.8f;
    public static final float FLOOR_OFFSET = 0.1f;
    public static final float SIDE_CLEARANCE = 2.5f;
    public static final float TOP_CLEARANCE = 2.5f;
    public static final int BLANK_TEXT_LINE_WIDTH = 4;
    public static final float HALF_EXTENT = (PLAYER_WIDTH * 0.5f) + SIDE_CLEARANCE;
    public static final float PRISM_WIDTH = HALF_EXTENT * 2.0f;
    public static final float CEILING_Y = PLAYER_HEIGHT + TOP_CLEARANCE;
    public static final float PRISM_HEIGHT = CEILING_Y - FLOOR_OFFSET;
    public static final float WALL_CENTER_Y = FLOOR_OFFSET + (PRISM_HEIGHT * 0.5f);

    // Vanilla TextDisplay renders text/background as a text block, not a centered quad.
    // For our single-space blank text, the background resolves to a native 5px x 10px panel
    // centered at (0.5px, 5px) after the client text transform.
    private static final float TEXT_RENDER_PIXEL_SCALE = 0.025f;
    private static final float BLANK_TEXT_ADVANCE_PX = 4.0f;
    private static final float SINGLE_LINE_TEXT_HEIGHT_PX = 9.0f;
    private static final float BACKGROUND_PADDING_PX = 1.0f;
    private static final float BLANK_BACKGROUND_WIDTH = BLANK_TEXT_ADVANCE_PX + BACKGROUND_PADDING_PX;
    private static final float BLANK_BACKGROUND_HEIGHT = SINGLE_LINE_TEXT_HEIGHT_PX + BACKGROUND_PADDING_PX;
    private static final float BLANK_NATIVE_WIDTH = BLANK_BACKGROUND_WIDTH * TEXT_RENDER_PIXEL_SCALE;
    private static final float BLANK_NATIVE_HEIGHT = BLANK_BACKGROUND_HEIGHT * TEXT_RENDER_PIXEL_SCALE;
    private static final float BLANK_NATIVE_CENTER_X = 0.5f * TEXT_RENDER_PIXEL_SCALE;
    private static final float BLANK_NATIVE_CENTER_Y = (BLANK_BACKGROUND_HEIGHT * 0.5f) * TEXT_RENDER_PIXEL_SCALE;

    private static final float HALF_PI = (float) (Math.PI / 2.0);
    private static final float PI = (float) Math.PI;
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

    public static Vector3f faceCenterOffset(OverlayFace face) {
        FaceLayout layout = layout(face);
        return new Vector3f(layout.centerX(), layout.centerY(), layout.centerZ());
    }

    public static Matrix4f localFaceTransform(OverlayFace face) {
        return new Matrix4f()
                .translation(localFaceTranslation(face))
                .rotate(localFaceRotation(face))
                .scale(localFaceScale(face));
    }

    public static Vector3f localFaceTranslation(OverlayFace face) {
        Quaternionf rotation = localFaceRotation(face);
        Vector3f scaledCenter = nativeBackgroundCenter().mul(localFaceScale(face));
        rotation.transform(scaledCenter);
        return scaledCenter.negate();
    }

    public static Quaternionf localFaceRotation(OverlayFace face) {
        return switch (face) {
            case FRONT -> new Quaternionf();
            case RIGHT -> new Quaternionf().rotationY(-HALF_PI);
            case BACK -> new Quaternionf().rotationY(PI);
            case LEFT -> new Quaternionf().rotationY(HALF_PI);
            case UP -> new Quaternionf().rotationX(HALF_PI);
            case DOWN -> new Quaternionf().rotationX(-HALF_PI);
        };
    }

    public static Vector3f localFaceScale(OverlayFace face) {
        FaceLayout layout = layout(face);
        return new Vector3f(layout.width() / BLANK_NATIVE_WIDTH, layout.height() / BLANK_NATIVE_HEIGHT, 1.0f);
    }

    public static float faceWidth(OverlayFace face) {
        return layout(face).width();
    }

    public static float faceHeight(OverlayFace face) {
        return layout(face).height();
    }

    static float nativeBackgroundWidth() {
        return BLANK_NATIVE_WIDTH;
    }

    static float nativeBackgroundHeight() {
        return BLANK_NATIVE_HEIGHT;
    }

    static Vector3f nativeBackgroundCenter() {
        return new Vector3f(BLANK_NATIVE_CENTER_X, BLANK_NATIVE_CENTER_Y, 0.0f);
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

    private record FaceLayout(float centerX, float centerY, float centerZ, float width, float height) {
    }
}
