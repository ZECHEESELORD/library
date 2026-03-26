package sh.harold.creative.library.menu.core;

import sh.harold.creative.library.menu.AccentFamily;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuTab;

import java.util.List;

final class RepresentativeMenuFixtures {

    private RepresentativeMenuFixtures() {
    }

    static MenuButton yourSkyBlockProfile() {
        return MenuButton.builder(MenuIcon.vanilla("player_head"))
                .name("Your SkyBlock Profile")
                .description("View your equipment, stats, and more!")
                .lines(
                        "✦ Speed 361",
                        "❁ Strength 399.75",
                        "❈ Defense 786",
                        "☠ Crit Damage 306.5%",
                        "☣ Crit Chance 144.5%",
                        "❤ Health 2,524",
                        "✎ Intelligence 1,563.14",
                        "and more...")
                .line("Also accessible via /stats")
                .action(ActionVerb.VIEW, context -> { })
                .build();
    }

    static MenuButton farmingXlix() {
        return MenuButton.builder(MenuIcon.vanilla("golden_hoe"))
                .name("Farming XLIX")
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
                .name("Museum Rewards")
                .description("Every 100 SkyBlock XP obtained from your Museum, Eleanor will reward you.")
                .line("Special Items do not reward SkyBlock XP.")
                .pairs(
                        "Total XP", "395/3,522",
                        "Milestone", "3/40")
                .progress("Progress to Milestone 4", 35, 100, AccentFamily.AQUA)
                .action(ActionVerb.VIEW, context -> { })
                .build();
    }

    static MenuButton profileSlotFive() {
        return MenuButton.builder(MenuIcon.vanilla("gray_dye"))
                .name("Profile Slot #5")
                .secondary("Unavailable")
                .pair("Cost", "2,750 SkyBlock Gems")
                .pair("You have", "360 Gems")
                .line("Cannot afford this!")
                .action(ActionVerb.OPEN, context -> { })
                .build();
    }

    static Menu tabbedGalleryMenu() {
        return new StandardMenuService().tabs()
                .title("House Style Gallery")
                .defaultTab("profiles")
                .addTab(MenuTab.of("profiles", "Profiles", MenuIcon.vanilla("player_head"),
                        List.of(yourSkyBlockProfile(), profileSlotFive())))
                .addTab(MenuTab.of("progress", "Progress", MenuIcon.vanilla("experience_bottle"),
                        List.of(farmingXlix(), museumRewards())))
                .build();
    }

    static List<MenuItem> representativeItems() {
        return List.of(
                yourSkyBlockProfile(),
                farmingXlix(),
                museumRewards(),
                profileSlotFive());
    }
}
