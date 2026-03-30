package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuSlot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuGoldenFixtureTest {

    @Test
    void representativeCardsKeepStyledTitleColors() {
        List<MenuItem> items = RepresentativeMenuFixtures.representativeItems();

        assertEquals(List.of(
                        TextColor.color(0xFFFF55),
                        TextColor.color(0x55FF55),
                        TextColor.color(0xFFAA00),
                        TextColor.color(0xAAAAAA)),
                items.stream()
                        .map(item -> HouseMenuCompiler.compile(13, item).title().color())
                        .toList());
    }

    @Test
    void representativeCardsMatchGoldenOutput() {
        List<MenuItem> items = RepresentativeMenuFixtures.representativeItems();

        String snapshot = snapshot(HouseMenuCompiler.compile(13, items.get(0)))
                + "\n---\n"
                + snapshot(HouseMenuCompiler.compile(13, items.get(1)))
                + "\n---\n"
                + snapshot(HouseMenuCompiler.compile(13, items.get(2)))
                + "\n---\n"
                + snapshot(HouseMenuCompiler.compile(13, items.get(3)));

        assertEquals("""
                SLOT 13
                TITLE Your SkyBlock Profile
                LORE
                View your equipment,
                stats, and more!
                
                ✦ Speed 361
                ❁ Strength 399.75
                ❈ Defense 786
                ☠ Crit Damage 306.5%
                ☣ Crit Chance 144.5%
                ❤ Health 2,524
                ✎ Intelligence 1,563.14
                and more...
                
                Also accessible via /stats
                
                CLICK to view!
                ---
                SLOT 13
                TITLE Farming XLIX
                LORE
                Harvest crops and shear
                sheep to earn Farming XP!
                
                Progress to Level L: 85.8%
                -------------------- 3,432,908.3/4,000,000
                
                • Farmhand L
                • Grants +196 to +200
                  Farming Fortune
                • +5 Health
                • +1,000,000 Coins
                • +20 SkyBlock XP
                
                CLICK to view!
                ---
                SLOT 13
                TITLE Museum Rewards
                LORE
                Every 100 SkyBlock XP
                obtained from your Museum,
                Eleanor will reward you.
                
                Special Items do not reward SkyBlock XP.
                
                Total XP: 395/3,522
                Milestone: 3/40
                
                Progress to Milestone 4: 35%
                -------------------- 35/100
                
                CLICK to view!
                ---
                SLOT 13
                TITLE Profile Slot #5
                LORE
                Unavailable
                
                Cost: 2,750 SkyBlock Gems
                
                You have: 360 Gems
                
                Cannot afford this!
                
                CLICK to open!""", snapshot);
    }

    @Test
    void tabbedGalleryMenuMatchesGoldenLayout() {
        Menu menu = RepresentativeMenuFixtures.groupedTabGalleryMenu();

        assertEquals("""
                GEOMETRY TABS
                INITIAL tab:oak:nav:0:page:0
                FRAME tab:oak:nav:0:page:0
                1:Oak
                4:Dark Oak
                5:[black_stained_glass_pane]
                6:Stone
                10:[lime_stained_glass_pane glow]
                11:[gray_stained_glass_pane]
                19:Oak Planks
                49:Close""", menuSnapshot(menu));
    }

    private static String snapshot(MenuSlot slot) {
        String lore = slot.lore().stream().map(MenuGoldenFixtureTest::flatten).reduce((left, right) -> left + "\n" + right).orElse("");
        return "SLOT " + slot.slot() + "\nTITLE " + flatten(slot.title()) + "\nLORE\n" + lore;
    }

    private static String menuSnapshot(Menu menu) {
        StringBuilder builder = new StringBuilder();
        builder.append("GEOMETRY ").append(menu.geometry()).append('\n');
        builder.append("INITIAL ").append(menu.initialFrameId()).append('\n');
        String frameId = menu.initialFrameId();
        builder.append("FRAME ").append(frameId);
        for (int slot : List.of(1, 4, 5, 6, 10, 11, 19, 49)) {
            builder.append('\n').append(slot).append(':').append(slotSnapshot(menu.frames().get(frameId).slots().get(slot)));
        }
        return builder.toString();
    }

    private static String slotSnapshot(MenuSlot slot) {
        String title = flatten(slot.title());
        if (!title.isBlank()) {
            return title;
        }
        String icon = slot.icon().key().replace("minecraft:", "");
        return slot.glow() ? "[" + icon + " glow]" : "[" + icon + "]";
    }

    private static String flatten(Component component) {
        StringBuilder builder = new StringBuilder();
        append(builder, component);
        return builder.toString();
    }

    private static void append(StringBuilder builder, Component component) {
        if (component instanceof TextComponent textComponent) {
            builder.append(textComponent.content());
        }
        for (Component child : component.children()) {
            append(builder, child);
        }
    }
}
