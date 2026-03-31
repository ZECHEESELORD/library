package sh.harold.creative.library.example.paper;

import net.kyori.adventure.text.ComponentLike;
import org.bukkit.Material;
import sh.harold.creative.library.menu.AccentFamily;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuPair;
import sh.harold.creative.library.menu.MenuTab;
import sh.harold.creative.library.menu.MenuTabContent;
import sh.harold.creative.library.menu.MenuTabGroup;
import sh.harold.creative.library.menu.paper.PaperMenuPlatform;
import sh.harold.creative.library.sound.SoundCueKeys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

final class PaperMenuExampleMenus {

    private static final TabGalleryVariant DEFAULT_TABS_GALLERY = new TabGalleryVariant(true, "profiles");

    private final PaperMenuPlatform menus;
    private final Map<TabGalleryVariant, Menu> tabGalleryCache = new HashMap<>();

    PaperMenuExampleMenus(PaperMenuPlatform menus) {
        this.menus = menus;
        tabGalleryCache.put(DEFAULT_TABS_GALLERY, buildTabsGallery(true, "profiles"));
    }

    Menu gallery() {
        return tabsGallery();
    }

    Menu tabsGallery() {
        return tabsGallery(DEFAULT_TABS_GALLERY.canvasFillerEnabled(), DEFAULT_TABS_GALLERY.defaultTabId());
    }

    private Menu tabsGallery(boolean canvasFillerEnabled, String defaultTabId) {
        return tabGalleryCache.computeIfAbsent(new TabGalleryVariant(canvasFillerEnabled, defaultTabId),
                variant -> buildTabsGallery(variant.canvasFillerEnabled(), variant.defaultTabId()));
    }

    private Menu buildTabsGallery(boolean canvasFillerEnabled, String defaultTabId) {
        return menus.tabs()
                .title("House Style Gallery")
                .defaultTab(defaultTabId)
                .addGroup(MenuTabGroup.of(
                        "account",
                        List.of(
                                listTab("profiles", FakeSkyBlockMenuTitles.normal("Profiles"), Material.PLAYER_HEAD,
                                        "Browse your core profile surfaces, gear storage, and utility menus from one account-focused tab.",
                                        MenuPair.of("Highlight", FakeSkyBlockMenuValues.tracked("Accessory Power")),
                                        MenuPair.of("Status", FakeSkyBlockMenuValues.ready("Wardrobe ready")),
                                        List.of(
                                                yourSkyBlockProfileButton(),
                                                profileSlotFiveButton(),
                                                exampleButton(Material.ENDER_CHEST, FakeSkyBlockMenuTitles.normal("Accessory Bag"),
                                                "Review talisman loadouts and stored enrichments.", ActionVerb.VIEW),
                                                exampleButton(Material.LEATHER_CHESTPLATE, FakeSkyBlockMenuTitles.normal("Wardrobe"),
                                                        "Manage saved combat and farming sets.", ActionVerb.MANAGE))),
                                listTab("progress", FakeSkyBlockMenuTitles.success("Progress & Milestones"), Material.EXPERIENCE_BOTTLE,
                                        "Review skill progress, milestone rewards, and collection checkpoints without leaving the broader progression lane.",
                                        MenuPair.of("Track", FakeSkyBlockMenuValues.tracked("Skills & collections")),
                                        MenuPair.of("Hot", FakeSkyBlockMenuValues.claimable("Museum Rewards")),
                                        List.of(
                                                farmingXlixButton(),
                                                museumRewardsButton(),
                                                exampleButton(Material.DIAMOND_SWORD, FakeSkyBlockMenuTitles.danger("Slayer"),
                                                "Check boss milestones and recipe unlocks.", ActionVerb.VIEW),
                                                exampleButton(Material.HAY_BLOCK, FakeSkyBlockMenuTitles.normal("Collections"),
                                                        "Browse crop and material progression.", ActionVerb.BROWSE))),
                                listTab("museum", FakeSkyBlockMenuTitles.reward("Museum & Rewards"), Material.BOOK,
                                        "Check donated sets, milestone payouts, and the next curator unlocks in one reward-heavy museum tab.",
                                        MenuPair.of("Focus", FakeSkyBlockMenuValues.claimable("Rewards & donations")),
                                        MenuPair.of("Next", FakeSkyBlockMenuValues.tracked("Curator Log")),
                                        List.of(
                                                museumRewardsButton(),
                                                exampleButton(Material.PAINTING, FakeSkyBlockMenuTitles.normal("Donations"),
                                                "Review donated pieces and missing sets.", ActionVerb.BROWSE),
                                                exampleButton(Material.ARMOR_STAND, FakeSkyBlockMenuTitles.normal("Armor Sets"),
                                                "Track set milestones and SkyBlock XP.", ActionVerb.VIEW),
                                                exampleButton(Material.CLOCK, FakeSkyBlockMenuTitles.reward("Curator Log"),
                                                        "See which rewards unlock next.", ActionVerb.VIEW))),
                                listTab("upgrades", FakeSkyBlockMenuTitles.special("Account & Profile Upgrades"), Material.NETHER_STAR,
                                        "Upgrade your current account unlocks, long-term profile slots, and quality-of-life expansions from one place.",
                                        MenuPair.of("Profile", FakeSkyBlockMenuValues.inactive("Nothing Going On...")),
                                        MenuPair.of("Account", FakeSkyBlockMenuValues.featured("Bazaar Flipper II")),
                                        List.of(
                                                exampleButton(Material.CRAFTING_TABLE, FakeSkyBlockMenuTitles.normal("Crafting Slots"),
                                                "Review unlocked recipe pages and extra slots.", ActionVerb.MANAGE),
                                                exampleButton(Material.REDSTONE, FakeSkyBlockMenuTitles.perk("Accessory Power"),
                                                "Tune your selected reforging preset.", ActionVerb.MANAGE),
                                                exampleButton(Material.EMERALD, FakeSkyBlockMenuTitles.perk("Bank Upgrades"),
                                                "Check interest tiers and account perks.", ActionVerb.BUY),
                                                exampleButton(Material.ANVIL, FakeSkyBlockMenuTitles.perk("Profile Upgrades"),
                                                        "Preview bag, potion, and wardrobe expansions.", ActionVerb.BROWSE)))
                        )))
                .addGroup(MenuTabGroup.of(
                        "social",
                        List.of(
                                listTab("party", FakeSkyBlockMenuTitles.success("Party & Queue Tools"), Material.CAKE,
                                        "Coordinate quick group runs, invite teammates, and confirm readiness before the party leaves the staging screen.",
                                        MenuPair.of("Ready", FakeSkyBlockMenuValues.ready("Ready Check")),
                                        MenuPair.of("Browse", FakeSkyBlockMenuValues.tracked("Party Finder")),
                                        List.of(
                                                exampleButton(Material.NAME_TAG, FakeSkyBlockMenuTitles.normal("Party Finder"),
                                                "Browse open groups for quick dungeon runs.", ActionVerb.BROWSE),
                                                exampleButton(Material.PLAYER_HEAD, FakeSkyBlockMenuTitles.normal("Invite Friends"),
                                                "Send invites to your frequent teammates.", ActionVerb.OPEN),
                                                exampleButton(Material.BELL, FakeSkyBlockMenuTitles.success("Ready Check"),
                                                        "Signal the group before starting a run.", ActionVerb.CONFIRM))),
                                listTab("guild", FakeSkyBlockMenuTitles.perk("Guild & Perk Tools"), Material.SHIELD,
                                        "Review the roster, browse recent activity, and surface guild-wide perks without bouncing between admin views.",
                                        MenuPair.of("Perks", FakeSkyBlockMenuValues.tracked("Beacon boosts")),
                                        MenuPair.of("Roster", FakeSkyBlockMenuValues.ready("Online notes synced")),
                                        List.of(
                                                exampleButton(Material.BOOKSHELF, FakeSkyBlockMenuTitles.normal("Roster"),
                                                "View online members and rank notes.", ActionVerb.BROWSE),
                                                exampleButton(Material.BEACON, FakeSkyBlockMenuTitles.perk("Perks"),
                                                "Inspect guild upgrade bonuses.", ActionVerb.VIEW),
                                                exampleButton(Material.WRITABLE_BOOK, FakeSkyBlockMenuTitles.normal("Activity Log"),
                                                        "Browse recent joins, leaves, and events.", ActionVerb.BROWSE))),
                                listTab("mail", FakeSkyBlockMenuTitles.reward("Mail, Refunds & Claims"), Material.WRITABLE_BOOK,
                                        "Collect completed deliveries, stale refunds, and marketplace returns from one claim-first mailbox tab.",
                                        MenuPair.of("Claim", FakeSkyBlockMenuValues.claimable("Deliveries & bids")),
                                        MenuPair.of("Queue", FakeSkyBlockMenuValues.claimable("26 parcels waiting")),
                                        mailButtons())
                        )))
                .addGroup(MenuTabGroup.of(
                        "showcase",
                        List.of(
                                canvasTab("overview", FakeSkyBlockMenuTitles.perk("Layout Overview"), Material.COMPASS,
                                        "Review the tab layout rules, centered row-three placements, and when negative space helps a custom canvas breathe.",
                                        builder -> {
                                    if (!canvasFillerEnabled) {
                                        builder.fillWithBlackPane(false);
                                    }
                                    builder.place(31, infoDisplay(Material.COMPASS, FakeSkyBlockMenuTitles.perk("Centered Canvas"),
                                            "Canvas tabs should bias primary content to row 3, centered first.",
                                            "One item: (4,3)",
                                            "Two items: (2,3) and (6,3)",
                                            "Expand outward from there."));
                                },
                                        "Centered row-3 placements first",
                                        "Leave list tabs partially open",
                                        "Use filler only when it helps"),
                                canvasTab("showcase", FakeSkyBlockMenuTitles.special("Featured Cards"), Material.ITEM_FRAME,
                                        "Preview a couple of fully authored cards side by side so title tone, spacing, and grouped blocks read together.",
                                        builder -> {
                                    if (!canvasFillerEnabled) {
                                        builder.fillWithBlackPane(false);
                                    }
                                    builder.place(29, yourSkyBlockProfileDisplay());
                                    builder.place(33, farmingXlixDisplay());
                                },
                                        "Colored titles by feel",
                                        "Grouped stat and progress blocks",
                                        "Prompt-last house copy"),
                                canvasTab("routes", FakeSkyBlockMenuTitles.normal("Demo Routes"), Material.BOOKSHELF,
                                        "Jump between the list and canvas galleries while toggling the shared filler so the authored layout decisions stay easy to compare.",
                                        builder -> {
                                            if (!canvasFillerEnabled) {
                                                builder.fillWithBlackPane(false);
                                            }
                                            builder.place(29, openMenuButton(Material.CHEST, FakeSkyBlockMenuTitles.normal("Open List Gallery"),
                                                    "Jump into the plain paged list example.", ActionVerb.OPEN, this::listGallery));
                                            builder.place(31, canvasFillerToggleButton(canvasFillerEnabled, "routes"));
                                            builder.place(33, openMenuButton(Material.ITEM_FRAME, FakeSkyBlockMenuTitles.normal("Open Canvas Gallery"),
                                                    "Jump into the fixed-slot canvas example.", ActionVerb.OPEN, this::canvasGallery));
                                        },
                                        "Open paged list gallery",
                                        "Toggle canvas filler live",
                                        "Open fixed-slot canvas gallery")
                        )))
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

    private MenuButton yourSkyBlockProfileButton() {
        return menus.button(Material.PLAYER_HEAD)
                .name(FakeSkyBlockMenuTitles.normal("Your SkyBlock Profile"))
                .description("View your equipment, stats, and more!")
                .valueLines(FakeSkyBlockMenuValues.profileStats())
                .line("Also accessible via /stats")
                .action(ActionVerb.VIEW, context -> context.open(yourSkyBlockProfileMenu()))
                .build();
    }

    private MenuButton farmingXlixButton() {
        return menus.button(Material.GOLDEN_HOE)
                .name(FakeSkyBlockMenuTitles.success("Farming XLIX"))
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
                .name(FakeSkyBlockMenuTitles.reward("Museum Rewards"))
                .description("Every 100 SkyBlock XP obtained from your Museum, Eleanor will reward you.")
                .line("Special Items do not reward SkyBlock XP.")
                .pairs(
                        MenuPair.of("Total XP", FakeSkyBlockMenuValues.totalXp(395, 3_522)),
                        MenuPair.of("Milestone", FakeSkyBlockMenuValues.milestone(3, 40)))
                .progress("Progress to Milestone 4", 35, 100, AccentFamily.AQUA)
                .action(ActionVerb.VIEW, context -> context.open(museumRewardsMenu()))
                .build();
    }

    private List<MenuItem> mailButtons() {
        ArrayList<MenuItem> items = new ArrayList<>();
        items.add(exampleButton(Material.CHEST_MINECART, FakeSkyBlockMenuTitles.reward("Deliveries"), "Claim gifted items and pending deliveries.", ActionVerb.CLAIM));
        items.add(exampleButton(Material.GOLD_INGOT, FakeSkyBlockMenuTitles.reward("Auction Bids"), "Track bid refunds and won listings.", ActionVerb.CLAIM));
        items.add(exampleButton(Material.WHEAT, FakeSkyBlockMenuTitles.reward("Bazaar Orders"), "Collect completed orders and expired fills.", ActionVerb.CLAIM));
        IntStream.rangeClosed(1, 26)
                .mapToObj(i -> exampleButton(Material.PAPER, FakeSkyBlockMenuTitles.reward("Delivery Parcel " + i),
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
                galleryExampleButton(Material.ENDER_CHEST, FakeSkyBlockMenuTitles.normal("Accessory Bag"),
                        "Review talisman loadouts and stored enrichments.", ActionVerb.MANAGE,
                        FakeSkyBlockMenuValues.detail("Selected Power", "Silky"),
                        FakeSkyBlockMenuValues.detail("Saved Layouts", 5)),
                galleryExampleButton(Material.LEATHER_CHESTPLATE, FakeSkyBlockMenuTitles.normal("Wardrobe"),
                        "Swap between saved combat, mining, and farming sets.", ActionVerb.MANAGE,
                        FakeSkyBlockMenuValues.detail("Active Set", "Crimson"),
                        FakeSkyBlockMenuValues.detail("Quick Equip", "Enabled")),
                galleryExampleButton(Material.ARMOR_STAND, FakeSkyBlockMenuTitles.normal("Equipment Rack"),
                        "Inspect your belt, cloak, necklace, and gauntlet setup.", ActionVerb.VIEW,
                        FakeSkyBlockMenuValues.detail("Combat Power", 1_924),
                        FakeSkyBlockMenuValues.detail("Missing Slots", 0)),
                galleryExampleButton(Material.CHEST, FakeSkyBlockMenuTitles.normal("Ender Chest Pages"),
                        "Open storage pages for overflow tools, trophies, and fuels.", ActionVerb.OPEN,
                        FakeSkyBlockMenuValues.detail("Unlocked Pages", "5/9"),
                        FakeSkyBlockMenuValues.detail("Overflow Slots", 12)),
                galleryExampleButton(Material.POTION, FakeSkyBlockMenuTitles.perk("Potion Bag"),
                        "Review active brews, stored effects, and God Potion coverage.", ActionVerb.OPEN,
                        FakeSkyBlockMenuValues.detail("Favorite Brew", "Harvest Harbinger"),
                        FakeSkyBlockMenuValues.detail("Auto Consume", "Off")),
                galleryExampleButton(Material.ARROW, FakeSkyBlockMenuTitles.normal("Quiver"),
                        "Manage arrow swaps for Dragons, Kuudra, and dungeon runs.", ActionVerb.MANAGE,
                        FakeSkyBlockMenuValues.detail("Selected Arrow", "Armorshred"),
                        FakeSkyBlockMenuValues.detail("Stored Types", 4)),
                galleryExampleButton(Material.BARREL, FakeSkyBlockMenuTitles.normal("Sack of Sacks"),
                        "Browse compact sacks for crops, ores, and trophy fish.", ActionVerb.BROWSE,
                        FakeSkyBlockMenuValues.detail("Filled Sacks", 11),
                        FakeSkyBlockMenuValues.detail("Pickup Mode", "Enabled")),
                galleryExampleButton(Material.BONE, FakeSkyBlockMenuTitles.normal("Pet Menu"),
                        "Check active pet perks, held items, and pet-score progress.", ActionVerb.VIEW,
                        FakeSkyBlockMenuValues.detail("Active Pet", "Elephant"),
                        FakeSkyBlockMenuValues.detail("Pet Score", 418)),
                galleryExampleButton(Material.COMPASS, FakeSkyBlockMenuTitles.special("Travel Scrollbook"),
                        "Jump between islands with your unlocked travel scrolls.", ActionVerb.OPEN,
                        FakeSkyBlockMenuValues.detail("Pinned Route", "Garden"),
                        FakeSkyBlockMenuValues.detail("Unlocked Scrolls", 9)),
                galleryExampleButton(Material.MAP, FakeSkyBlockMenuTitles.normal("Garden Plot Map"),
                        "Preview crop layouts, visitor access, and sprinkler coverage.", ActionVerb.VIEW,
                        FakeSkyBlockMenuValues.detail("Barn Skin", "Oak"),
                        FakeSkyBlockMenuValues.detail("Unlocked Plots", 13)),
                galleryExampleButton(Material.HOPPER, FakeSkyBlockMenuTitles.perk("Composter"),
                        "Track fuel timers, organic matter, and crop upgrades.", ActionVerb.MANAGE,
                        FakeSkyBlockMenuValues.detail("Fuel Time", "5h 42m"),
                        FakeSkyBlockMenuValues.detail("Organic Matter", "84%")),
                galleryExampleButton(Material.WRITABLE_BOOK, FakeSkyBlockMenuTitles.normal("Visitor Queue"),
                        "Review pending Garden visitors and their shopping lists.", ActionVerb.BROWSE,
                        FakeSkyBlockMenuValues.detail("Waiting Visitors", 3),
                        FakeSkyBlockMenuValues.detail("Best Offer", FakeSkyBlockMenuValues.copper(1_200))),
                galleryExampleButton(Material.WHEAT, FakeSkyBlockMenuTitles.reward("Bazaar Orders"),
                        "Collect completed fills and rebalance your active buy orders.", ActionVerb.CLAIM,
                        FakeSkyBlockMenuValues.detail("Instant Sell", "Off"),
                        FakeSkyBlockMenuValues.detail("Open Orders", 7)),
                galleryExampleButton(Material.GOLD_INGOT, FakeSkyBlockMenuTitles.reward("Auction House"),
                        "Monitor bids, sold listings, and your current watchlist.", ActionVerb.BROWSE,
                        FakeSkyBlockMenuValues.detail("Won Auctions", 2),
                        FakeSkyBlockMenuValues.detail("Watchlist Items", 14)),
                galleryExampleButton(Material.EMERALD, FakeSkyBlockMenuTitles.perk("Bank Upgrades"),
                        "Review interest tiers, coop access, and coin cap upgrades.", ActionVerb.BUY,
                        FakeSkyBlockMenuValues.detail("Interest Tier", "Premier"),
                        FakeSkyBlockMenuValues.detail("Coop Withdrawals", "On")),
                galleryExampleButton(Material.ANVIL, FakeSkyBlockMenuTitles.normal("Forge Queue"),
                        "Check Dwarven forge timers and recipe progress at a glance.", ActionVerb.MANAGE,
                        FakeSkyBlockMenuValues.detail("Active Slots", "3/5"),
                        FakeSkyBlockMenuValues.detail("Next Ready", "Refined Titanium")),
                galleryExampleButton(Material.DIAMOND_SWORD, FakeSkyBlockMenuTitles.danger("Dungeon Classes"),
                        "Inspect class milestones, best runs, and selected loadouts.", ActionVerb.VIEW,
                        FakeSkyBlockMenuValues.detail("Favorite Class", "Berserk"),
                        FakeSkyBlockMenuValues.detail("Best Floor", "M7")),
                galleryExampleButton(Material.SPIDER_EYE, FakeSkyBlockMenuTitles.danger("Bestiary"),
                        "Browse family progress and combat XP from tracked mobs.", ActionVerb.BROWSE,
                        FakeSkyBlockMenuValues.detail("Families Maxed", 18),
                        FakeSkyBlockMenuValues.detail("Next Milestone", "Graveyard")),
                galleryExampleButton(Material.IRON_SWORD, FakeSkyBlockMenuTitles.danger("Slayer"),
                        "Review boss milestones, recipes, and daily boss streaks.", ActionVerb.VIEW,
                        FakeSkyBlockMenuValues.detail("Highest Tier", "Inferno IV"),
                        FakeSkyBlockMenuValues.detail("XP to Next", FakeSkyBlockMenuValues.xp(4_800))),
                galleryExampleButton(Material.HAY_BLOCK, FakeSkyBlockMenuTitles.normal("Collections"),
                        "Track crop, mob, and mining unlocks across your profile.", ActionVerb.BROWSE,
                        FakeSkyBlockMenuValues.detail("Maxed Collections", 21),
                        FakeSkyBlockMenuValues.detail("Nearest Unlock", "Cactus IX")),
                galleryExampleButton(Material.CRAFTING_TABLE, FakeSkyBlockMenuTitles.perk("Minions"),
                        "Check fuel setups, compactors, and storage links.", ActionVerb.MANAGE,
                        FakeSkyBlockMenuValues.detail("Working Minions", 24),
                        FakeSkyBlockMenuValues.detail("AFK Bucket", "Enabled")),
                galleryExampleButton(Material.CLOCK, FakeSkyBlockMenuTitles.reward("Jacob's Contests"),
                        "See your next crop schedule and personal best medals.", ActionVerb.VIEW,
                        FakeSkyBlockMenuValues.detail("Next Crop", "Nether Wart"),
                        FakeSkyBlockMenuValues.detail("Gold Medals", 19)),
                galleryExampleButton(Material.FISHING_ROD, FakeSkyBlockMenuTitles.special("Trophy Fishing"),
                        "Review your bronze-to-diamond catches and lava hotspots.", ActionVerb.VIEW,
                        FakeSkyBlockMenuValues.detail("Diamond Fish", 7),
                        FakeSkyBlockMenuValues.detail("Favorite Spot", "Burning Desert")),
                galleryExampleButton(Material.MAP, FakeSkyBlockMenuTitles.special("Crystal Hollows Map"),
                        "Open your scanned routes for nucleus runs and gemstone loops.", ActionVerb.OPEN,
                        FakeSkyBlockMenuValues.detail("Route Set", "Jasper South"),
                        FakeSkyBlockMenuValues.detail("Commission Path", "Loaded")),
                galleryExampleButton(Material.ENDER_PEARL, FakeSkyBlockMenuTitles.special("Rift Guide"),
                        "Browse tears, timecharms, and the next unlocked district.", ActionVerb.OPEN,
                        FakeSkyBlockMenuValues.detail("Time Left", "9m 34s"),
                        FakeSkyBlockMenuValues.detail("Next Unlock", "Living Cave")),
                galleryExampleButton(Material.MAGMA_CREAM, FakeSkyBlockMenuTitles.danger("Kuudra Progress"),
                        "Inspect chest tiers, follower faction rep, and key stock.", ActionVerb.VIEW,
                        FakeSkyBlockMenuValues.detail("Highest Tier", "Fiery"),
                        FakeSkyBlockMenuValues.detail("Heavy Pearls", 27)),
                galleryExampleButton(Material.BLAZE_POWDER, FakeSkyBlockMenuTitles.special("Crimson Isles Reputation"),
                        "Track Barbarian and Mage standing with your recent tasks.", ActionVerb.VIEW,
                        FakeSkyBlockMenuValues.detail("Barbarian Rep", 11),
                        FakeSkyBlockMenuValues.detail("Mage Rep", 9)),
                galleryExampleButton(Material.COOKIE, FakeSkyBlockMenuTitles.perk("Booster Cookie Buffs"),
                        "Review active buffs, bits income, and your stacked duration.", ActionVerb.VIEW,
                        FakeSkyBlockMenuValues.detail("Bits Available", FakeSkyBlockMenuValues.bits(10_420)),
                        FakeSkyBlockMenuValues.detail("Duration Left", "3d 5h")),
                galleryExampleButton(Material.PAPER, FakeSkyBlockMenuTitles.special("Bingo Card"),
                        "Open the seasonal goals board and unclaimed challenge marks.", ActionVerb.OPEN,
                        FakeSkyBlockMenuValues.detail("Challenges Done", 12),
                        FakeSkyBlockMenuValues.detail("Unclaimed Rewards", 2)),
                galleryExampleButton(Material.MINECART, FakeSkyBlockMenuTitles.special("SkyMall Daily"),
                        "Preview today's Dwarven passives and the next reroll window.", ActionVerb.VIEW,
                        FakeSkyBlockMenuValues.detail("Today's Buff", "Lucky Looting"),
                        FakeSkyBlockMenuValues.detail("Reroll In", "18h")));
    }

    private MenuButton profileSlotFiveButton() {
        return menus.button(Material.GRAY_DYE)
                .name(FakeSkyBlockMenuTitles.locked("Profile Slot #5"))
                .secondary("Unavailable")
                .pair("Cost", FakeSkyBlockMenuValues.skyBlockGems(2_750))
                .pair("You have", FakeSkyBlockMenuValues.gems(360))
                .line("Cannot afford this!")
                .action(ActionVerb.OPEN, context -> context.open(profileSlotFiveMenu()))
                .sound(SoundCueKeys.RESULT_DENY)
                .build();
    }

    private MenuTab listTab(
            String id,
            ComponentLike name,
            Material material,
            String description,
            MenuPair firstSummary,
            MenuPair secondSummary,
            Iterable<? extends MenuItem> items
    ) {
        return menus.tab(id, material)
                .name(name)
                .description(description)
                .pairs(firstSummary, secondSummary)
                .items(items)
                .build();
    }

    private MenuTab canvasTab(
            String id,
            ComponentLike name,
            Material material,
            String description,
            Consumer<MenuTabContent.CanvasBuilder> consumer,
            String... summaryBullets
    ) {
        return menus.tab(id, material)
                .name(name)
                .description(description)
                .bullets(summaryBullets)
                .canvas(consumer)
                .build();
    }

    private MenuButton exampleButton(Material material, ComponentLike name, String description, ActionVerb verb) {
        return menus.button(material)
                .name(name)
                .description(description)
                .action(verb, context -> { })
                .build();
    }

    private MenuButton galleryExampleButton(
            Material material,
            ComponentLike name,
            String description,
            ActionVerb verb,
            MenuPair... details
    ) {
        var builder = menus.button(material)
                .name(name)
                .description(description);
        if (details.length > 0) {
            builder.pairs(details);
        }
        return builder.action(verb, context -> { }).build();
    }

    private MenuButton canvasFillerToggleButton(boolean enabled, String tabId) {
        return menus.button(Material.LEVER)
                .name(enabled
                        ? FakeSkyBlockMenuTitles.success("Canvas Filler: On")
                        : FakeSkyBlockMenuTitles.locked("Canvas Filler: Off"))
                .description("Toggle the black stained-glass filler behind custom tab layouts.")
                .action(ActionVerb.TOGGLE, context -> context.open(tabsGallery(!enabled, tabId)))
                .build();
    }

    private MenuButton openMenuButton(
            Material material,
            ComponentLike name,
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

    private MenuDisplayItem infoDisplay(Material material, ComponentLike name, String description, String... lines) {
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
                .name(FakeSkyBlockMenuTitles.normal("Your SkyBlock Profile"))
                .description("View your equipment, stats, and more!")
                .valueLines(FakeSkyBlockMenuValues.profileStats())
                .line("Also accessible via /stats")
                .build();
    }

    private MenuDisplayItem farmingXlixDisplay() {
        return menus.display(Material.GOLDEN_HOE)
                .name(FakeSkyBlockMenuTitles.success("Farming XLIX"))
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
                .name(FakeSkyBlockMenuTitles.reward("Museum Rewards"))
                .description("Every 100 SkyBlock XP obtained from your Museum, Eleanor will reward you.")
                .line("Special Items do not reward SkyBlock XP.")
                .pairs(
                        MenuPair.of("Total XP", FakeSkyBlockMenuValues.totalXp(395, 3_522)),
                        MenuPair.of("Milestone", FakeSkyBlockMenuValues.milestone(3, 40)))
                .progress("Progress to Milestone 4", 35, 100, AccentFamily.AQUA)
                .build();
    }

    private MenuDisplayItem profileSlotFiveDisplay() {
        return menus.display(Material.GRAY_DYE)
                .name(FakeSkyBlockMenuTitles.locked("Profile Slot #5"))
                .secondary("Unavailable")
                .pair("Cost", FakeSkyBlockMenuValues.skyBlockGems(2_750))
                .pair("You have", FakeSkyBlockMenuValues.gems(360))
                .line("Cannot afford this!")
                .build();
    }

    private record TabGalleryVariant(boolean canvasFillerEnabled, String defaultTabId) {
    }
}
