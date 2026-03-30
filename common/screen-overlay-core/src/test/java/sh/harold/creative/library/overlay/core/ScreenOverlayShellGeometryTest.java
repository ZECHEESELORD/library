package sh.harold.creative.library.overlay.core;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenOverlayShellGeometryTest {

    @Test
    void shellUsesFixedFaceOrderAndRequestedFaceDimensions() {
        assertEquals(List.of(
                OverlayFace.FRONT,
                OverlayFace.RIGHT,
                OverlayFace.BACK,
                OverlayFace.LEFT,
                OverlayFace.UP,
                OverlayFace.DOWN
        ), ScreenOverlayShellGeometry.faces());

        assertEquals(5.6f, ScreenOverlayShellGeometry.faceWidth(OverlayFace.FRONT), 0.001f);
        assertEquals(4.2f, ScreenOverlayShellGeometry.faceHeight(OverlayFace.FRONT), 0.001f);
        assertEquals(5.6f, ScreenOverlayShellGeometry.faceWidth(OverlayFace.UP), 0.001f);
        assertEquals(5.6f, ScreenOverlayShellGeometry.faceHeight(OverlayFace.UP), 0.001f);
    }

    @Test
    void faceCenterOffsetsMatchThePrismWalls() {
        assertOffset(OverlayFace.FRONT, 0.0f, 2.2f, -2.8f);
        assertOffset(OverlayFace.RIGHT, 2.8f, 2.2f, 0.0f);
        assertOffset(OverlayFace.BACK, 0.0f, 2.2f, 2.8f);
        assertOffset(OverlayFace.LEFT, -2.8f, 2.2f, 0.0f);
        assertOffset(OverlayFace.UP, 0.0f, 4.3f, 0.0f);
        assertOffset(OverlayFace.DOWN, 0.0f, 0.1f, 0.0f);
    }

    @Test
    void localTransformCentersTheRealBlankTextBackgroundAtTheEntityOrigin() {
        for (OverlayFace face : ScreenOverlayShellGeometry.faces()) {
            Vector3f center = ScreenOverlayShellGeometry.localFaceTransform(face)
                    .transformPosition(ScreenOverlayShellGeometry.nativeBackgroundCenter());

            assertEquals(0.0f, center.x, 0.001f);
            assertEquals(0.0f, center.y, 0.001f);
            assertEquals(0.0f, center.z, 0.001f);
        }
    }

    @Test
    void localTransformExpandsTheNativeBlankSurfaceToEachRequestedFaceSize() {
        for (OverlayFace face : ScreenOverlayShellGeometry.faces()) {
            Vector3f min = transformedCorner(face, -0.5f, -0.5f);
            Vector3f maxX = transformedCorner(face, 0.5f, -0.5f);
            Vector3f maxY = transformedCorner(face, -0.5f, 0.5f);

            assertEquals(ScreenOverlayShellGeometry.faceWidth(face), min.distance(maxX), 0.001f);
            assertEquals(ScreenOverlayShellGeometry.faceHeight(face), min.distance(maxY), 0.001f);
        }
    }

    @Test
    void blankTextBackgroundMatchesTheVanillaSingleSpaceLayout() {
        assertEquals(0.125f, ScreenOverlayShellGeometry.nativeBackgroundWidth(), 0.001f);
        assertEquals(0.25f, ScreenOverlayShellGeometry.nativeBackgroundHeight(), 0.001f);

        Vector3f center = ScreenOverlayShellGeometry.nativeBackgroundCenter();
        assertEquals(0.0125f, center.x, 0.001f);
        assertEquals(0.125f, center.y, 0.001f);
        assertEquals(0.0f, center.z, 0.001f);
    }

    @Test
    void faceNormalsPointTowardThePrismInterior() {
        assertNormal(OverlayFace.FRONT, 0.0f, 0.0f, 1.0f);
        assertNormal(OverlayFace.RIGHT, -1.0f, 0.0f, 0.0f);
        assertNormal(OverlayFace.BACK, 0.0f, 0.0f, -1.0f);
        assertNormal(OverlayFace.LEFT, 1.0f, 0.0f, 0.0f);
        assertNormal(OverlayFace.UP, 0.0f, -1.0f, 0.0f);
        assertNormal(OverlayFace.DOWN, 0.0f, 1.0f, 0.0f);
    }

    private static void assertOffset(OverlayFace face, float x, float y, float z) {
        Vector3f offset = ScreenOverlayShellGeometry.faceCenterOffset(face);

        assertEquals(x, offset.x, 0.001f);
        assertEquals(y, offset.y, 0.001f);
        assertEquals(z, offset.z, 0.001f);
    }

    private static Vector3f transformedCorner(OverlayFace face, float normalizedX, float normalizedY) {
        Vector3f center = ScreenOverlayShellGeometry.nativeBackgroundCenter();
        float halfWidth = ScreenOverlayShellGeometry.nativeBackgroundWidth() * 0.5f;
        float halfHeight = ScreenOverlayShellGeometry.nativeBackgroundHeight() * 0.5f;
        Vector3f nativeCorner = new Vector3f(
                center.x + (normalizedX * halfWidth * 2.0f),
                center.y + (normalizedY * halfHeight * 2.0f),
                0.0f
        );
        Matrix4f transform = ScreenOverlayShellGeometry.localFaceTransform(face);
        return transform.transformPosition(nativeCorner);
    }

    private static void assertNormal(OverlayFace face, float x, float y, float z) {
        Vector3f normal = ScreenOverlayShellGeometry.localFaceRotation(face)
                .transform(new Vector3f(0.0f, 0.0f, 1.0f));

        assertEquals(x, normal.x, 0.001f);
        assertEquals(y, normal.y, 0.001f);
        assertEquals(z, normal.z, 0.001f);
    }
}
