package sh.harold.library.entity.capability;

import sh.harold.library.entity.SkinTexture;

import java.util.Optional;

public interface SkinCapable {

    Optional<SkinTexture> skin();

    void skin(SkinTexture skinTexture);

    void clearSkin();
}
