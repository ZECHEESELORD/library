package sh.harold.library.spatial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuaternionTest {

    private static final double EPS = 1.0e-9;

    @Test
    void axisAngleRotatesRightHanded() {
        Vec3 rotated = Quaternion.axisAngle(Vec3.UNIT_Y, Math.PI / 2.0).rotate(Vec3.UNIT_X);
        assertEquals(0.0, rotated.x(), EPS);
        assertEquals(-1.0, rotated.z(), EPS);
    }

    @Test
    void logAndExpRoundTrip() {
        Vec3 v = new Vec3(0.3, -1.1, 0.7);
        Vec3 back = Quaternion.exp(v).log();
        assertEquals(v.x(), back.x(), EPS);
        assertEquals(v.y(), back.y(), EPS);
        assertEquals(v.z(), back.z(), EPS);
    }

    @Test
    void slerpTakesTheShortArcAndComposes() {
        Quaternion a = Quaternion.axisAngle(Vec3.UNIT_Z, 0.2);
        Quaternion b = Quaternion.axisAngle(Vec3.UNIT_Z, 1.0).negate();
        assertEquals(0.0, a.slerp(b, 0.5).angleTo(Quaternion.axisAngle(Vec3.UNIT_Z, 0.6)), EPS);
        Quaternion composed = a.multiply(Quaternion.axisAngle(Vec3.UNIT_Z, 0.3));
        assertEquals(0.0, composed.angleTo(Quaternion.axisAngle(Vec3.UNIT_Z, 0.5)), EPS);
        assertEquals(0.0, a.multiply(a.inverse()).angleTo(Quaternion.IDENTITY), EPS);
    }
}
