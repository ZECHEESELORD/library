package sh.harold.creative.library.example.minestom;

import net.minestom.server.item.Material;
import sh.harold.creative.library.menu.AccentFamily;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuTabGroup;
import sh.harold.creative.library.menu.minestom.MinestomMenuPlatform;
import sh.harold.creative.library.sound.SoundCueKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

final class MinestomMenuExampleMenus {

    private final MinestomMenuPlatform menus;

    MinestomMenuExampleMenus(MinestomMenuPlatform menus) {
        this.menus = menus;
    }

    Menu gallery() {
        return tabsGallery();
    }

    Menu tabsGallery() {
        return tabsGallery(true, "profiles");
    }

    private Menu tabsGallery(boolean canvasFillerEnabled, String defaultTabId) {
        return menus.tabs()
                .title("House Style Gallery")
                .defaultTab(defaultTabId)
                .addGroup(MenuTabGroup.of(
                        "account",
                        List.of(
                                menus.tab("profiles", "Profiles", Material.PLAYER_HEAD, List.of(
                                        yourSkyBlockProfileButton(),
                                        profileSlotFiveButton(),
                                        exampleButton(Material.ENDER_CHEST, "Accessory Bag",
                                                "Review talisman loadouts and stored enrichments.", ActionVerb.VIEW),
                                        exampleButton(Material.LEATHER_CHESTPLATE, "Wardrobe",
                                                "Manage saved combat and farming sets.", ActionVerb.MANAGE))),
                                menus.tab("progress", "Progress", Material.EXPERIENCE_BOTTLE, List.of(
                                        farmingXlixButton(),
                                        museumRewardsButton(),
                                        exampleButton(Material.DIAMOND_SWORD, "Slayer",
                                                "Check boss milestones and recipe unlocks.", ActionVerb.VIEW),
                                        exampleButton(Material.HAY_BLOCK, "Collections",
                                                "Browse crop and material progression.", ActionVerb.BROWSE))),
                                menus.tab("museum", "Museum", Material.BOOK, List.of(
                                        museumRewardsButton(),
                                        exampleButton(Material.PAINTING, "Donations",
                                                "Review donated pieces and missing sets.", ActionVerb.BROWSE),
                                        exampleButton(Material.ARMOR_STAND, "Armor Sets",
                                                "Track set milestones and SkyBlock XP.", ActionVerb.VIEW),
                                        exampleButton(Material.CLOCK, "Curator Log",
                                                "See which rewards unlock next.", ActionVerb.VIEW))),
                                menus.tab("upgrades", "Upgrades", Material.NETHER_STAR, List.of(
                                        exampleButton(Material.CRAFTING_TABLE, "Crafting Slots",
                                                "Review unlocked recipe pages and extra slots.", ActionVerb.MANAGE),
                                        exampleButton(Material.REDSTONE, "Accessory Power",
                                                "Tune your selected reforging preset.", ActionVerb.MANAGE),
                                        exampleButton(Material.EMERALD, "Bank Upgrades",
                                                "Check interest tiers and account perks.", ActionVerb.BUY),
                                        exampleButton(Material.ANVIL, "Profile Upgrades",
                                                "Preview bag, potion, and wardrobe expansions.", ActionVerb.BROWSE)))))
                )
                .addGroup(MenuTabGroup.of(
                        "social",
                        List.of(
                                menus.tab("party", "Party", Material.CAKE, List.of(
                                        exampleButton(Material.NAME_TAG, "Party Finder",
                                                "Browse open groups for quick dungeon runs.", ActionVerb.BROWSE),
                                        exampleButton(Material.PLAYER_HEAD, "Invite Friends",
                                                "Send invites to your frequent teammates.", ActionVerb.OPEN),
                                        exampleButton(Material.BELL, "Ready Check",
                                                "Signal the group before starting a run.", ActionVerb.CONFIRM))),
                                menus.tab("guild", "Guild", Material.SHIELD, List.of(
                                        exampleButton(Material.BOOKSHELF, "Roster",
                                                "View online members and rank notes.", ActionVerb.BROWSE),
                                        exampleButton(Material.BEACON, "Perks",
                                                "Inspect guild upgrade bonuses.", ActionVerb.VIEW),
                                        exampleButton(Material.WRITABLE_BOOK, "Activity Log",
                                                "Browse recent joins, leaves, and events.", ActionVerb.BROWSE))),
                                menus.tab("mail", "Mail", Material.WRITABLE_BOOK, mailButtons())))
                )
                .addGroup(MenuTabGroup.of(
                        "showcase",
                        List.of(
                                menus.tab("overview", "Overview", Material.COMPASS, builder -> {
                                    if (!canvasFillerEnabled) {
                                        builder.fillWithBlackPane(false);
                                    }
                                    builder.place(31, infoDisplay(Material.COMPASS, "Centered Canvas",
                                            "Canvas tabs should bias primary content to row 3, centered first.",
                                            "One item: (4,3)",
                                            "Two items: (2,3) and (6,3)",
                                            "Expand outward from there."));
                                }),
                                menus.tab("showcase", "Showcase", Material.ITEM_FRAME, builder -> {
                                    if (!canvasFillerEnabled) {
                                        builder.fillWithBlackPane(false);
                                    }
                                    builder.place(29, yourSkyBlockProfileDisplay());
                                    builder.place(33, farmingXlixDisplay());
                                }),
                                menus.tab("routes", "Routes", Material.BOOKSHELF, builder -> {
                                    if (!canvasFillerEnabled) {
                                        builder.fillWithBlackPane(false);
                                    }
                                    builder.place(29, openMenuButton(Material.CHEST, "Open List Gallery",
                                            "Jump into the plain paged list example.", ActionVerb.OPEN, this::listGallery));
                                    builder.place(31, canvasFillerToggleButton(canvasFillerEnabled, "routes"));
                                    builder.place(33, openMenuButton(Material.ITEM_FRAME, "Open Canvas Gallery",
                                            "Jump into the fixed-slot canvas example.", ActionVerb.OPEN, this::canvasGallery));
                                })))
                )
                .build();
    }

    Menu listGallery() {
        return menus.list()
                .title("House Style List Gallery")
                .addItem(yourSkyBlockProfileButton())
                .addItem(farmingXlixButton())
                .addItem(museumRewardsButton())
                .addItem(profileSlotFiveButton())
                .build();
    }

    Menu canvasGallery() {
        return menus.canvas()
                .title("Canvas Gallery")
                .place(10, yourSkyBlockProfileDisplay())
                .place(12, farmingXlixDisplay())
                .place(14, museumRewardsDisplay())
                .place(16, profileSlotFiveDisplay())
                .build();
    }

    Menu profilePreview() {
        return yourSkyBlockProfileMenu();
    }

    Menu farmingPreview() {
        return farmingXlixMenu();
    }

    Menu museumPreview() {
        return museumRewardsMenu();
    }

    Menu slotFivePreview() {
        return profileSlotFiveMenu();
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

    private List<MenuItem> mailButtons() {
        ArrayList<MenuItem> items = new ArrayList<>();
        items.add(exampleButton(Material.CHEST_MINECART, "Deliveries", "Claim gifted items and pending deliveries.", ActionVerb.CLAIM));
        items.add(exampleButton(Material.GOLD_INGOT, "Auction Bids", "Track bid refunds and won listings.", ActionVerb.CLAIM));
        items.add(exampleButton(Material.WHEAT, "Bazaar Orders", "Collect completed orders and expired fills.", ActionVerb.CLAIM));
        IntStream.rangeClosed(1, 26)
                .mapToObj(i -> exampleButton(Material.PAPER, "Delivery Parcel " + i,
                        "Claim pending mailbox rewards and refunds.", ActionVerb.CLAIM))
                .forEach(items::add);
        return List.copyOf(items);
    }

    private MenuButton profileSlotFiveButton() {
        return menus.button(Material.GRAY_DYE)
                .name("Profile Slot #5")
                .secondary("Unavailable")
                .pair("Cost", "2,750 SkyBlock Gems")
                .pair("You have", "360 Gems")
                .line("Cannot afford this!")
                .action(ActionVerb.OPEN, context -> context.open(profileSlotFiveMenu()))
                .sound(SoundCueKeys.RESULT_DENY)
                .build();
    }

    private MenuButton exampleButton(Material material, String name, String description, ActionVerb verb) {
        return menus.button(material)
                .name(name)
                .description(description)
                .action(verb, context -> { })
                .build();
    }

    private MenuButton canvasFillerToggleButton(boolean enabled, String tabId) {
        return menus.button(Material.LEVER)
                .name(enabled ? "Canvas Filler: On" : "Canvas Filler: Off")
                .description("Toggle the black stained-glass filler behind custom tab layouts.")
                .action(ActionVerb.TOGGLE, context -> context.open(tabsGallery(!enabled, tabId)))
                .build();
    }

    private MenuButton openMenuButton(
            Material material,
            String name,
            String description,
            ActionVerb verb,
            Supplier<Menu> target
    ) {
        return menus.button(material)
                .name(name)
                .description(description)
                .action(verb, context -> context.open(target.get()))
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
                .place(13, item)
                .build();
    }

    private MenuDisplayItem infoDisplay(Material material, String name, String description, String... lines) {
        var builder = menus.display(material)
                .name(name)
                .description(description);
        if (lines.length > 0) {
            builder.lines(lines);
        }
        return builder.build();
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
