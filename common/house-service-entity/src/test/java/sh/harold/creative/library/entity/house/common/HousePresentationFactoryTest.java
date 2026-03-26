package sh.harold.creative.library.entity.house.common;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.entity.EntitySpec;
import sh.harold.creative.library.entity.EntityTypes;
import sh.harold.creative.library.entity.house.HousePresentation;
import sh.harold.creative.library.entity.house.HousePresentationFactory;
import sh.harold.creative.library.entity.house.HousePromptMode;
import sh.harold.creative.library.entity.house.HouseRole;
import sh.harold.creative.library.entity.house.HouseServiceSpec;
import sh.harold.creative.library.entity.house.HouseValidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HousePresentationFactoryTest {

    @Test
    void missingNameFailsValidation() {
        HouseServiceSpec spec = HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.VILLAGER).build())
                .role(HouseRole.of(Component.text("Banker")))
                .build();

        assertThrows(IllegalArgumentException.class, () -> HouseValidator.validate(spec));
    }

    @Test
    void missingRoleFailsUnlessHidden() {
        HouseServiceSpec spec = HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.VILLAGER).build())
                .name(Component.text("Meredith"))
                .role(HouseRole.of(Component.empty()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> HouseValidator.validate(spec));
    }

    @Test
    void promptIsAlwaysRenderedAsThirdLine() {
        HouseServiceSpec spec = HouseServiceSpec.builder(EntitySpec.builder(EntityTypes.VILLAGER).build())
                .name(Component.text("Meredith"))
                .role(HouseRole.of(Component.text("Banker")))
                .promptMode(HousePromptMode.OPEN)
                .build();

        HousePresentation presentation = HousePresentationFactory.create(spec);

        assertEquals(Component.text("Meredith"), presentation.lines().get(0));
        assertEquals(Component.text("Banker"), presentation.lines().get(1));
        assertEquals(Component.text("CLICK to OPEN"), presentation.lines().get(2));
    }
}
