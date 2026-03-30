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
                .addItems(listGalleryButtons())
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

    private List<MenuItem> listGalleryButtons() {
        return List.of(
                yourSkyBlockProfileButton(),
                farmingXlixButton(),
                museumRewardsButton(),
                profileSlotFiveButton(),
                galleryExampleButton(Material.ENDER_CHEST, "Accessory Bag",
                        "Review talisman loadouts and stored enrichments.", ActionVerb.MANAGE,
                        "Selected Power: Silky",
                        "Saved Layouts: 5"),
                galleryExampleButton(Material.LEATHER_CHESTPLATE, "Wardrobe",
                        "Swap between saved combat, mining, and farming sets.", ActionVerb.MANAGE,
                        "Active Set: Crimson",
                        "Quick Equip: Enabled"),
                galleryExampleButton(Material.ARMOR_STAND, "Equipment Rack",
                        "Inspect your belt, cloak, necklace, and gauntlet setup.", ActionVerb.VIEW,
                        "Combat Power: 1,924",
                        "Missing Slots: 0"),
                galleryExampleButton(Material.CHEST, "Ender Chest Pages",
                        "Open storage pages for overflow tools, trophies, and fuels.", ActionVerb.OPEN,
                        "Unlocked Pages: 5/9",
                        "Overflow Slots: 12"),
                galleryExampleButton(Material.POTION, "Potion Bag",
                        "Review active brews, stored effects, and God Potion coverage.", ActionVerb.OPEN,
                        "Favorite Brew: Harvest Harbinger",
                        "Auto Consume: Off"),
                galleryExampleButton(Material.ARROW, "Quiver",
                        "Manage arrow swaps for Dragons, Kuudra, and dungeon runs.", ActionVerb.MANAGE,
                        "Selected Arrow: Armorshred",
                        "Stored Types: 4"),
                galleryExampleButton(Material.BARREL, "Sack of Sacks",
                        "Browse compact sacks for crops, ores, and trophy fish.", ActionVerb.BROWSE,
                        "Filled Sacks: 11",
                        "Pickup Mode: Enabled"),
                galleryExampleButton(Material.BONE, "Pet Menu",
                        "Check active pet perks, held items, and pet-score progress.", ActionVerb.VIEW,
                        "Active Pet: Elephant",
                        "Pet Score: 418"),
                galleryExampleButton(Material.COMPASS, "Travel Scrollbook",
                        "Jump between islands with your unlocked travel scrolls.", ActionVerb.OPEN,
                        "Pinned Route: Garden",
                        "Unlocked Scrolls: 9"),
                galleryExampleButton(Material.MAP, "Garden Plot Map",
                        "Preview crop layouts, visitor access, and sprinkler coverage.", ActionVerb.VIEW,
                        "Barn Skin: Oak",
                        "Unlocked Plots: 13"),
                galleryExampleButton(Material.HOPPER, "Composter",
                        "Track fuel timers, organic matter, and crop upgrades.", ActionVerb.MANAGE,
                        "Fuel Time: 5h 42m",
                        "Organic Matter: 84%"),
                galleryExampleButton(Material.WRITABLE_BOOK, "Visitor Queue",
                        "Review pending Garden visitors and their shopping lists.", ActionVerb.BROWSE,
                        "Waiting Visitors: 3",
                        "Best Offer: 1,200 Copper"),
                galleryExampleButton(Material.WHEAT, "Bazaar Orders",
                        "Collect completed fills and rebalance your active buy orders.", ActionVerb.CLAIM,
                        "Instant Sell: Off",
                        "Open Orders: 7"),
                galleryExampleButton(Material.GOLD_INGOT, "Auction House",
                        "Monitor bids, sold listings, and your current watchlist.", ActionVerb.BROWSE,
                        "Won Auctions: 2",
                        "Watchlist Items: 14"),
                galleryExampleButton(Material.EMERALD, "Bank Upgrades",
                        "Review interest tiers, coop access, and coin cap upgrades.", ActionVerb.BUY,
                        "Interest Tier: Premier",
                        "Coop Withdrawals: On"),
                galleryExampleButton(Material.ANVIL, "Forge Queue",
                        "Check Dwarven forge timers and recipe progress at a glance.", ActionVerb.MANAGE,
                        "Active Slots: 3/5",
                        "Next Ready: Refined Titanium"),
                galleryExampleButton(Material.DIAMOND_SWORD, "Dungeon Classes",
                        "Inspect class milestones, best runs, and selected loadouts.", ActionVerb.VIEW,
                        "Favorite Class: Berserk",
                        "Best Floor: M7"),
                galleryExampleButton(Material.SPIDER_EYE, "Bestiary",
                        "Browse family progress and combat XP from tracked mobs.", ActionVerb.BROWSE,
                        "Families Maxed: 18",
                        "Next Milestone: Graveyard"),
                galleryExampleButton(Material.IRON_SWORD, "Slayer",
                        "Review boss milestones, recipes, and daily boss streaks.", ActionVerb.VIEW,
                        "Highest Tier: Inferno IV",
                        "XP to Next: 4,800"),
                galleryExampleButton(Material.HAY_BLOCK, "Collections",
                        "Track crop, mob, and mining unlocks across your profile.", ActionVerb.BROWSE,
                        "Maxed Collections: 21",
                        "Nearest Unlock: Cactus IX"),
                galleryExampleButton(Material.CRAFTING_TABLE, "Minions",
                        "Check fuel setups, compactors, and storage links.", ActionVerb.MANAGE,
                        "Working Minions: 24",
                        "AFK Bucket: Enabled"),
                galleryExampleButton(Material.CLOCK, "Jacob's Contests",
                        "See your next crop schedule and personal best medals.", ActionVerb.VIEW,
                        "Next Crop: Nether Wart",
                        "Gold Medals: 19"),
                galleryExampleButton(Material.FISHING_ROD, "Trophy Fishing",
                        "Review your bronze-to-diamond catches and lava hotspots.", ActionVerb.VIEW,
                        "Diamond Fish: 7",
                        "Favorite Spot: Burning Desert"),
                galleryExampleButton(Material.MAP, "Crystal Hollows Map",
                        "Open your scanned routes for nucleus runs and gemstone loops.", ActionVerb.OPEN,
                        "Route Set: Jasper South",
                        "Commission Path: Loaded"),
                galleryExampleButton(Material.ENDER_PEARL, "Rift Guide",
                        "Browse tears, timecharms, and the next unlocked district.", ActionVerb.OPEN,
                        "Time Left: 9m 34s",
                        "Next Unlock: Living Cave"),
                galleryExampleButton(Material.MAGMA_CREAM, "Kuudra Progress",
                        "Inspect chest tiers, follower faction rep, and key stock.", ActionVerb.VIEW,
                        "Highest Tier: Fiery",
                        "Heavy Pearls: 27"),
                galleryExampleButton(Material.BLAZE_POWDER, "Crimson Isles Reputation",
                        "Track Barbarian and Mage standing with your recent tasks.", ActionVerb.VIEW,
                        "Barbarian Rep: 11",
                        "Mage Rep: 9"),
                galleryExampleButton(Material.COOKIE, "Booster Cookie Buffs",
                        "Review active buffs, bits income, and your stacked duration.", ActionVerb.VIEW,
                        "Bits Available: 10,420",
                        "Duration Left: 3d 5h"),
                galleryExampleButton(Material.PAPER, "Bingo Card",
                        "Open the seasonal goals board and unclaimed challenge marks.", ActionVerb.OPEN,
                        "Challenges Done: 12",
                        "Unclaimed Rewards: 2"),
                galleryExampleButton(Material.MINECART, "SkyMall Daily",
                        "Preview today's Dwarven passives and the next reroll window.", ActionVerb.VIEW,
                        "Today's Buff: Lucky Looting",
                        "Reroll In: 18h"));
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

    private MenuButton galleryExampleButton(Material material, String name, String description, ActionVerb verb, String... details) {
        var builder = menus.button(material)
                .name(name)
                .description(description);
        if (details.length == 1) {
            builder.line(details[0]);
        } else if (details.length > 1) {
            builder.lines(details);
        }
        return builder.action(verb, context -> { }).build();
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
