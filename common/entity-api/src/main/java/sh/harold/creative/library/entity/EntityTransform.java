package sh.harold.creative.library.entity;

public record EntityTransform(double x, double y, double z, float yaw, float pitch) {

    public static EntityTransform at(double x, double y, double z) {
        return new EntityTransform(x, y, z, 0.0f, 0.0f);
    }
}
