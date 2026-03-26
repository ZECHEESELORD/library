package sh.harold.creative.library.entity.capability;

import sh.harold.creative.library.entity.SkinTexture;

import java.util.Optional;

public interface SkinCapable {

    Optional<SkinTexture> skin();

    void skin(SkinTexture skinTexture);

    void clearSkin();
}
