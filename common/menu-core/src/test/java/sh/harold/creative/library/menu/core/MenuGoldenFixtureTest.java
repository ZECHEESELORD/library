package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuSlot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuGoldenFixtureTest {

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
                
                CLICK to view
                ---
                SLOT 13
                TITLE Farming XLIX
                LORE
                Harvest crops and
                shear sheep to
                earn Farming XP!
                
                Progress to Level L: 85.8%
                -------------------- 3,432,908.3/4,000,000
                
                • Farmhand L
                • Grants +196 to
                  +200 Farming
                  Fortune
                • +5 Health
                • +1,000,000 Coins
                • +20 SkyBlock XP
                
                CLICK to view
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
                
                CLICK to view
                ---
                SLOT 13
                TITLE Profile Slot #5
                LORE
                Unavailable
                
                Cost: 2,750 SkyBlock Gems
                
                You have: 360 Gems
                
                Cannot afford this!
                
                CLICK to open""", snapshot);
    }

    @Test
    void tabbedGalleryMenuMatchesGoldenLayout() {
        Menu menu = RepresentativeMenuFixtures.tabbedGalleryMenu();

        assertEquals("""
                GEOMETRY TABS
                INITIAL tab:profiles:page:0
                FRAME tab:profiles:page:0
                0:» Profiles
                1:Progress
                9:Your SkyBlock Profile
                10:Profile Slot #5
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
        for (int slot : List.of(0, 1, 9, 10, 49)) {
            builder.append('\n').append(slot).append(':').append(flatten(menu.frames().get(frameId).slots().get(slot).title()));
        }
        return builder.toString();
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
