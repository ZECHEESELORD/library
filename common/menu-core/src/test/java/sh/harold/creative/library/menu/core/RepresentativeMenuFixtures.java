package sh.harold.creative.library.menu.core;

import sh.harold.creative.library.menu.AccentFamily;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuPair;
import sh.harold.creative.library.menu.MenuTab;
import sh.harold.creative.library.menu.MenuTabGroup;

import java.util.List;

final class RepresentativeMenuFixtures {

    private RepresentativeMenuFixtures() {
    }

    static MenuButton yourSkyBlockProfile() {
        return MenuButton.builder(MenuIcon.vanilla("player_head"))
                .name(FakeSkyBlockMenuTitles.normal("Your SkyBlock Profile"))
                .description("View your equipment, stats, and more!")
                .valueLines(FakeSkyBlockMenuValues.profileStats())
                .line("Also accessible via /stats")
                .action(ActionVerb.VIEW, context -> { })
                .build();
    }

    static MenuButton farmingXlix() {
        return MenuButton.builder(MenuIcon.vanilla("golden_hoe"))
                .name(FakeSkyBlockMenuTitles.success("Farming XLIX"))
                .description("Harvest crops and shear sheep to earn Farming XP!")
                .progress("Progress to Level L", 3_432_908.3, 4_000_000, AccentFamily.GOLD)
                .bullets(
                        "Farmhand L",
                        "Grants +196 to +200 Farming Fortune",
                        "+5 Health",
                        "+1,000,000 Coins",
                        "+20 SkyBlock XP")
                .action(ActionVerb.VIEW, context -> { })
                .build();
    }

    static MenuButton museumRewards() {
        return MenuButton.builder(MenuIcon.vanilla("book"))
                .name(FakeSkyBlockMenuTitles.reward("Museum Rewards"))
                .description("Every 100 SkyBlock XP obtained from your Museum, Eleanor will reward you.")
                .line("Special Items do not reward SkyBlock XP.")
                .pairs(
                        MenuPair.of("Total XP", FakeSkyBlockMenuValues.totalXp(395, 3_522)),
                        MenuPair.of("Milestone", FakeSkyBlockMenuValues.milestone(3, 40)))
                .progress("Progress to Milestone 4", 35, 100, AccentFamily.AQUA)
                .action(ActionVerb.VIEW, context -> { })
                .build();
    }

    static MenuButton profileSlotFive() {
        return MenuButton.builder(MenuIcon.vanilla("gray_dye"))
                .name(FakeSkyBlockMenuTitles.locked("Profile Slot #5"))
                .secondary("Unavailable")
                .pair("Cost", FakeSkyBlockMenuValues.skyBlockGems(2_750))
                .pair("You have", FakeSkyBlockMenuValues.gems(360))
                .line("Cannot afford this!")
                .action(ActionVerb.OPEN, context -> { })
                .build();
    }

    static Menu groupedTabGalleryMenu() {
        return new StandardMenuService().tabs()
                .title("Block Browser")
                .defaultTab("oak")
                .addGroup(MenuTabGroup.of("wood", List.of(
                        MenuTab.of("oak", "Oak", MenuIcon.vanilla("oak_planks"), List.of(namedButton("Oak Planks"))),
                        MenuTab.of("acacia", "Acacia", MenuIcon.vanilla("acacia_planks"), List.of(namedButton("Acacia Planks"))),
                        MenuTab.of("cedar", "Cedar", MenuIcon.vanilla("spruce_planks"), List.of(namedButton("Cedar Planks"))),
                        MenuTab.of("dark-oak", "Dark Oak", MenuIcon.vanilla("dark_oak_planks"), List.of(namedButton("Dark Oak Planks")))
                )))
                .addGroup(MenuTabGroup.of("stone", List.of(
                        MenuTab.of("stone", "Stone", MenuIcon.vanilla("stone"), List.of(namedButton("Stone Blocks"))),
                        MenuTab.of("cobble", "Cobblestone", MenuIcon.vanilla("cobblestone"), List.of(namedButton("Cobblestone Blocks")))
                )))
                .build();
    }

    static List<MenuItem> representativeItems() {
        return List.of(
                yourSkyBlockProfile(),
                farmingXlix(),
                museumRewards(),
                profileSlotFive());
    }

    private static MenuButton namedButton(String name) {
        return MenuButton.builder(MenuIcon.vanilla("stone"))
                .name(name)
                .action(ActionVerb.VIEW, context -> { })
                .build();
    }
}
