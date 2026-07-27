package sh.harold.library.entity.house;

import net.kyori.adventure.text.Component;
import sh.harold.library.entity.EntityTypes;

import java.util.Objects;

public final class HouseValidator {

    private HouseValidator() {
    }

    public static void validate(HouseServiceSpec spec) {
        Objects.requireNonNull(spec, "spec");
        if (spec.name() == null || Component.empty().equals(spec.name())) {
            throw new IllegalArgumentException("House service name is required");
        }
        if (spec.description() == null || Component.empty().equals(spec.description())) {
            throw new IllegalArgumentException("House service description is required");
        }
        if (spec.behaviorProfile().isPresent()
                && !EntityTypes.PLAYER_LIKE_HUMANOID.equals(spec.entitySpec().type())) {
            throw new IllegalArgumentException(
                    "A House behaviorProfile requires creative:player_like_humanoid"
            );
        }
    }
}
