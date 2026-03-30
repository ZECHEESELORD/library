package sh.harold.creative.library.entity.house;

import net.kyori.adventure.text.Component;

import java.util.UUID;

public final class HousePresentationFactory {

    private HousePresentationFactory() {
    }

    public static HousePresentation create(HouseServiceSpec spec) {
        HouseValidator.validate(spec);
        return new HousePresentation(
                HouseTextFormats.displayName(spec.name()),
                HouseTextFormats.description(spec.description()),
                HouseTextFormats.prompt()
        );
    }

    public static Component anchorName(UUID id) {
        return HouseTextFormats.anchorName(id);
    }
}
