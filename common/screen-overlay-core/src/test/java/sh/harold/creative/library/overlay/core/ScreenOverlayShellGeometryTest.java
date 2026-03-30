package sh.harold.creative.library.overlay.core;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenOverlayShellGeometryTest {

    @Test
    void shellUsesFixedFaceOrderAndScale() {
        assertEquals(List.of(
                OverlayFace.FRONT,
                OverlayFace.RIGHT,
                OverlayFace.BACK,
                OverlayFace.LEFT,
                OverlayFace.UP,
                OverlayFace.DOWN
        ), ScreenOverlayShellGeometry.faces());

        Matrix4f front = ScreenOverlayShellGeometry.faceTransform(OverlayFace.FRONT);
        Matrix4f up = ScreenOverlayShellGeometry.faceTransform(OverlayFace.UP);
        Vector3f scale = front.getScale(new Vector3f());
        Vector3f topScale = up.getScale(new Vector3f());

        assertEquals(5.6f, scale.x, 0.001f);
        assertEquals(3.2f, scale.y, 0.001f);
        assertEquals(1.0f, scale.z, 0.001f);
        assertEquals(5.6f, topScale.x, 0.001f);
        assertEquals(5.6f, topScale.y, 0.001f);
        assertEquals(1.0f, topScale.z, 0.001f);
        assertEquals(5.6f, ScreenOverlayShellGeometry.faceWidth(OverlayFace.FRONT), 0.001f);
        assertEquals(3.2f, ScreenOverlayShellGeometry.faceHeight(OverlayFace.FRONT), 0.001f);
        assertEquals(5.6f, ScreenOverlayShellGeometry.faceWidth(OverlayFace.UP), 0.001f);
        assertEquals(5.6f, ScreenOverlayShellGeometry.faceHeight(OverlayFace.UP), 0.001f);
    }

    @Test
    void faceCentersMatchThePrismWalls() {
        assertCenter(OverlayFace.FRONT, 0.0f, 1.7f, -2.8f);
        assertCenter(OverlayFace.RIGHT, 2.8f, 1.7f, 0.0f);
        assertCenter(OverlayFace.BACK, 0.0f, 1.7f, 2.8f);
        assertCenter(OverlayFace.LEFT, -2.8f, 1.7f, 0.0f);
        assertCenter(OverlayFace.UP, 0.0f, 3.3f, 0.0f);
        assertCenter(OverlayFace.DOWN, 0.0f, 0.1f, 0.0f);
    }

    private static void assertCenter(OverlayFace face, float x, float y, float z) {
        Vector3f center = ScreenOverlayShellGeometry.faceTransform(face).transformPosition(new Vector3f(0.5f, 0.5f, 0.0f));

        assertEquals(x, center.x, 0.001f);
        assertEquals(y, center.y, 0.001f);
        assertEquals(z, center.z, 0.001f);
    }
}
