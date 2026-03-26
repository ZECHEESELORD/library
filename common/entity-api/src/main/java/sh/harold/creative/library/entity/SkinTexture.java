package sh.harold.creative.library.entity;

import java.util.Objects;

public record SkinTexture(String texture, String signature) {

    public SkinTexture {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(signature, "signature");
    }
}
