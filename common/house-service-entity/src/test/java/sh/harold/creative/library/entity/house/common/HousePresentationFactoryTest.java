package sh.harold.creative.library.entity.house.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.entity.EntitySpec;
import sh.harold.creative.library.entity.EntityTypes;
import sh.harold.creative.library.entity.house.HousePresentation;
import sh.harold.creative.library.entity.house.HousePresentationFactory;
import sh.harold.creative.library.entity.house.HouseServiceSpec;
import sh.harold.creative.library.entity.house.HouseValidator;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HousePresentationFactoryTest {

    @Test
    void missingNameFailsValidation() {
        HouseServiceSpec spec = HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.VILLAGER).build())
                .description(Component.text("Banker"))
                .build();

        assertThrows(IllegalArgumentException.class, () -> HouseValidator.validate(spec));
    }

    @Test
    void missingDescriptionFailsValidation() {
        HouseServiceSpec spec = HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.VILLAGER).build())
                .name(Component.text("Meredith"))
                .build();

        assertThrows(IllegalArgumentException.class, () -> HouseValidator.validate(spec));
    }

    @Test
    void presentationUsesStyledNameBracketedDescriptionAndFixedPrompt() {
        HouseServiceSpec spec = HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.VILLAGER).build())
                .name("&bMeredith")
                .description("Banker")
                .build();

        HousePresentation presentation = HousePresentationFactory.create(spec);

        assertEquals(Component.text("Meredith", NamedTextColor.AQUA), presentation.lines().get(0));
        assertEquals(
                Component.text()
                        .color(NamedTextColor.GRAY)
                        .append(Component.text("[", NamedTextColor.GRAY))
                        .append(Component.text("Banker", NamedTextColor.GRAY))
                        .append(Component.text("]", NamedTextColor.GRAY))
                        .build(),
                presentation.lines().get(1)
        );
        assertEquals(Component.text("CLICK", NamedTextColor.YELLOW, TextDecoration.BOLD), presentation.lines().get(2));
    }

    @Test
    void plainNameDefaultsToWhite() {
        HouseServiceSpec spec = HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.VILLAGER).build())
                .name("Meredith")
                .description(Component.text("Banker"))
                .build();

        HousePresentation presentation = HousePresentationFactory.create(spec);

        assertEquals(Component.text("Meredith", NamedTextColor.WHITE), presentation.name());
    }

    @Test
    void anchorNameUsesNpcPrefixAndUuidPrefix() {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        assertEquals(
                Component.text("[NPC] 123e4567", NamedTextColor.DARK_GRAY),
                HousePresentationFactory.anchorName(id)
        );
    }
}
