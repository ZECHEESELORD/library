package sh.harold.creative.library.example.paper;

import org.bukkit.Material;
import sh.harold.creative.library.menu.AccentFamily;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.paper.PaperMenuPlatform;

final class PaperMenuExampleMenus {

    private final PaperMenuPlatform menus;

    PaperMenuExampleMenus(PaperMenuPlatform menus) {
        this.menus = menus;
    }

    Menu gallery() {
        return menus.tabs()
                .title("House Style Gallery")
                .defaultTab("profiles")
                .addTab(menus.tab("profiles", "Profiles", Material.PLAYER_HEAD, java.util.List.of(
                        yourSkyBlockProfileButton(),
                        profileSlotFiveButton())))
                .addTab(menus.tab("progress", "Progress", Material.EXPERIENCE_BOTTLE, java.util.List.of(
                        farmingXlixButton(),
                        museumRewardsButton())))
                .build();
    }

    private MenuButton yourSkyBlockProfileButton() {
        return menus.button(Material.PLAYER_HEAD)
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
                .action(ActionVerb.VIEW, context -> context.open(yourSkyBlockProfileMenu()))
                .build();
    }

    private MenuButton farmingXlixButton() {
        return menus.button(Material.GOLDEN_HOE)
                .name("Farming XLIX")
                .description("Harvest crops and shear sheep to earn Farming XP!")
                .progress("Progress to Level L", 3_432_908.3, 4_000_000, AccentFamily.GOLD)
                .bullets(
                        "Farmhand L",
                        "Grants +196 to +200 Farming Fortune",
                        "+5 Health",
                        "+1,000,000 Coins",
                        "+20 SkyBlock XP")
                .action(ActionVerb.VIEW, context -> context.open(farmingXlixMenu()))
                .build();
    }

    private MenuButton museumRewardsButton() {
        return menus.button(Material.BOOK)
                .name("Museum Rewards")
                .description("Every 100 SkyBlock XP obtained from your Museum, Eleanor will reward you.")
                .line("Special Items do not reward SkyBlock XP.")
                .pairs(
                        "Total XP", "395/3,522",
                        "Milestone", "3/40")
                .progress("Progress to Milestone 4", 35, 100, AccentFamily.AQUA)
                .action(ActionVerb.VIEW, context -> context.open(museumRewardsMenu()))
                .build();
    }

    private MenuButton profileSlotFiveButton() {
        return menus.button(Material.GRAY_DYE)
                .name("Profile Slot #5")
                .secondary("Unavailable")
                .pair("Cost", "2,750 SkyBlock Gems")
                .pair("You have", "360 Gems")
                .line("Cannot afford this!")
                .action(ActionVerb.OPEN, context -> context.open(profileSlotFiveMenu()))
                .build();
    }

    private Menu yourSkyBlockProfileMenu() {
        return preview("Your SkyBlock Profile Preview", yourSkyBlockProfileDisplay());
    }

    private Menu farmingXlixMenu() {
        return preview("Farming XLIX Preview", farmingXlixDisplay());
    }

    private Menu museumRewardsMenu() {
        return preview("Museum Rewards Preview", museumRewardsDisplay());
    }

    private Menu profileSlotFiveMenu() {
        return preview("Profile Slot #5 Preview", profileSlotFiveDisplay());
    }

    private Menu preview(String title, MenuDisplayItem item) {
        return menus.canvas()
                .title(title)
                .back(context -> context.open(gallery()))
                .place(13, item)
                .build();
    }

    private MenuDisplayItem yourSkyBlockProfileDisplay() {
        return menus.display(Material.PLAYER_HEAD)
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
                .build();
    }

    private MenuDisplayItem farmingXlixDisplay() {
        return menus.display(Material.GOLDEN_HOE)
                .name("Farming XLIX")
                .description("Harvest crops and shear sheep to earn Farming XP!")
                .progress("Progress to Level L", 3_432_908.3, 4_000_000, AccentFamily.GOLD)
                .bullets(
                        "Farmhand L",
                        "Grants +196 to +200 Farming Fortune",
                        "+5 Health",
                        "+1,000,000 Coins",
                        "+20 SkyBlock XP")
                .build();
    }

    private MenuDisplayItem museumRewardsDisplay() {
        return menus.display(Material.BOOK)
                .name("Museum Rewards")
                .description("Every 100 SkyBlock XP obtained from your Museum, Eleanor will reward you.")
                .line("Special Items do not reward SkyBlock XP.")
                .pairs(
                        "Total XP", "395/3,522",
                        "Milestone", "3/40")
                .progress("Progress to Milestone 4", 35, 100, AccentFamily.AQUA)
                .build();
    }

    private MenuDisplayItem profileSlotFiveDisplay() {
        return menus.display(Material.GRAY_DYE)
                .name("Profile Slot #5")
                .secondary("Unavailable")
                .pair("Cost", "2,750 SkyBlock Gems")
                .pair("You have", "360 Gems")
                .line("Cannot afford this!")
                .build();
    }
}
