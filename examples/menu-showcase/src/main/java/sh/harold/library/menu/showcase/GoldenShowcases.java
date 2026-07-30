package sh.harold.library.menu.showcase;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import sh.harold.library.menu.ActionVerb;
import sh.harold.library.menu.CanvasMenuBuilder;
import sh.harold.library.menu.Menu;
import sh.harold.library.menu.MenuButton;
import sh.harold.library.menu.MenuChecklistEntry;
import sh.harold.library.menu.MenuDisplayItem;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.MenuItem;
import sh.harold.library.menu.MenuProgress;
import sh.harold.library.menu.MenuProgressPalette;
import sh.harold.library.menu.MenuService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class GoldenShowcases {

    private GoldenShowcases() {
    }

    static List<ShowcaseEntry> create(MenuService menus) {
        List<ShowcaseEntry> entries = new ArrayList<>();
        entries.add(golden("skyblock-menu", "SkyBlock Menu",
                "85deca1e294f13aaea52b8000e858a5ebb4985f9e2dcaaffed599dfdede0ddc8",
                "90ab14172d38e987899ae201b633b6cebd2977e94d6522661b5587f27f733b35", 13,
                skyBlockMenu(menus)));
        entries.add(golden("bazaar-oddities", "Bazaar Oddities",
                "6f926d5d0971b86f0c3139558b877aeebf1e00dd6e93a3c449125d06880c30a1",
                "bf346b49910a5b24e322efb4de7d4a2b318eca6a90035a91f884c0aac3af0149", 11,
                bazaarOddities(menus)));
        entries.add(golden("booster-cookie-detail", "Booster Cookie detail",
                "b4b8749e7cccee86a971007a6f40b489d6036a0cb7a143c4379a2e9096328cb6",
                "d5e2c171243ccc75b1f5bff1820b69078428252c23a3a001cc537512a5193620", 13,
                boosterCookie(menus)));
        entries.add(golden("bazaar-orders", "Bazaar Orders",
                "0af4ae3bfcb23fc49d17320d18aa775475ec8b318114735f112d22538af615c6",
                "6a9803d52e5142e0fabde239886730a3f087437540e64d2bc01b8584bc482d51", 10,
                bazaarOrders(menus)));
        entries.add(golden("tasks-core", "Tasks/Core",
                "2a2c42a04311a6228a38c9bd5b5d5dfde3fc1dc28da47e6f20f8c7abcab2f79b",
                "a3f7ed1a525b5d81c2b9727d3939a9e26347fda0172b412d880821b43ee97b2f", 4,
                tasksCore(menus)));
        entries.add(golden("skills", "Skills",
                "8b4fa65f835aaf35fa168720d32efe50a95f3802bb02a915cbdc5281d62bcf70",
                "d321866cc021c7d1ccca927251550b7d1b969180fab869bbaaabcf548bf7fd50", 20,
                skills(menus)));
        entries.add(golden("collections", "Collections",
                "6b6cc50d15867fbe0450171f127cab0686f78e2561085788ff53ccdab78bdcf2",
                "449acea6626f7166bb110248bd9e45818cb4c2a9e05fce3476cb4a32b9ac7410", 20,
                collections(menus)));
        entries.add(golden("bestiary", "Bestiary",
                "9415e6aa1119b4788aa2840c5c7c50792bd3c5729abac0a665ec1e29bc273cb6",
                "6565f84c67a01730366941d52472f76619d8e21b87124e46d543520813307540", 4,
                bestiary(menus)));
        entries.add(golden("community-shop", "Community Shop",
                "e6ad051cd65ce01e028181e26526017e9267ff9b2e649cc62301c93b38eb27fb",
                "c17e5d2249fd03138169a54905d8e3cb5ccebfb56d6d665a148d96b9cd9785c7", 3,
                communityShop(menus)));
        entries.add(golden("heart-of-forest", "Heart of Forest",
                "a05fd27ce606f682a52443391f3e8084335cf4ac6b82d8c894cc1b0905f6c20c",
                "2c9525a42cd43b9f13cd45680e9657acc89e99ec5e9b55dde0ad07a51a7b468e", 49,
                heartOfForest(menus)));
        entries.add(golden("fast-travel", "Fast Travel",
                "97fbaadb0700b32dfb0ca6b0181a11d848f07ac003c099f040c9df8bb546e509",
                "f26d3cf0b1d7b370a6b00432e623e8a44b6fca994354947da0739e2a079bc3ab", 11,
                fastTravel(menus)));
        entries.add(golden("profile-management", "Profile Management",
                "e16cf77fc71982b6344302536da9783652e2fdc343f8b32fd6d4dda1ef4ee062",
                "ac7879448c0c7344db55f8afbea8f2926eb234e8cb8e9015c92a5ea425cf7a83", 15,
                profileManagement(menus)));
        entries.add(golden("calendar", "Calendar",
                "38068bb11ec7a8016fc46fff8fa652c0d54e2bb9b74d77a168a1c7a844ce7bd2",
                "7abd62572019214c95865752060bf211b22042ef6398dc726a385659901aca75", 13,
                calendar(menus)));
        entries.add(golden("museum-milestones", "Museum Milestones",
                "b2d0ef8c2b678e6efa14897848cec902665b72803fef362474d9e5db03367294",
                "61255b3fa7ea40f1b5a4f93dc5ab20bc6a2b1ec4b8b0af0b53cb61f5b370fffa", 27,
                museumMilestones(menus)));
        entries.add(golden("chocolate-milestones", "Chocolate Milestones",
                "f7a7183fb798c69a1ae05a40e3966eef4d281a145ab8d699c57a9efa22e6f174",
                "328e0204f8ce1ea62f5cab9b679e37e2caa06a42e72fdbc8825d5e1fbb6ee2e1", 27,
                chocolateMilestones(menus)));
        entries.add(golden("stats-tuning", "Stats Tuning",
                "a647a8d2e0210f6b2efef708dab9afeb091783dafeb5056c95a1d5f00ba18139",
                "1ad205ba68046b99a544b4d8e26ced2c1704849accb3811484c523301ebffdd5", 4,
                statsTuning(menus)));
        entries.add(golden("personal-bank", "Personal Bank",
                "3538d364440fb4c459f561e668ff0570d2f5285c7ad23823e1684f72522240cd",
                "cac5f2497afbe88c3f3d83fccb28a29be089dc1273532a4d6f5b368fd725f6d2", 32,
                personalBank(menus)));
        entries.add(golden("bank-upgrades", "Bank Upgrades",
                "7a47b32bb8bfc5689743bbeb01620f3f19dcbf8c9f871ec120a9d06d2d5f24dd",
                "6b538c48a7cf558048ae3dc66681e5f6955bf2571461d47227bd0613251a304b", 19,
                bankUpgrades(menus)));
        entries.add(golden("supercraft", "Supercraft",
                "2898770de7f26699a1cdb1faa2e72a85cf0cfddb0bd6c2672275fe32450e8e0e",
                "c577fd55279936d0f06bd4c04117b1f5f9e62e55f4a9f5591291648bd9368e79", 32,
                supercraft(menus)));
        entries.add(golden("confirmation", "Confirmation",
                "d0d9469311c22423a25b6af3a9618c07a1270b99e12e372fef5b65cfb3404d65",
                "6f1af9678cde01d62bf7a8c68cd175f88bf33d9f06effb8f1798b747cadab460", 11,
                confirmation(menus), ShowcaseFeature.CONFIRMATION));
        return List.copyOf(entries);
    }

    private static Menu skyBlockMenu(MenuService menus) {
        MenuItem stats = MenuButton.builder(MenuIcon.vanilla("player_head"))
                .name(green("Stats & Equipment"))
                .description("View your equipment, stats, achievements, and more!")
                .section(section -> section
                        .valueLine(gray(" Speed "), aqua("362.5"))
                        .valueLine(gray(" Strength "), red("305.75"))
                        .valueLine(gray(" Defense "), green("800"))
                        .valueLine(gray(" Crit Damage "), aqua("306.5%"))
                        .valueLine(gray(" Crit Chance "), blue("144.5%"))
                        .valueLine(gray(" Health "), red("2,581"))
                        .valueLine(gray(" Intelligence "), aqua("1,609.01"))
                        .mutedLine("and more..."))
                .mutedLine("Also accessible via /stats")
                .onLeftClick(ActionVerb.VIEW, context -> { })
                .build();
        CanvasMenuBuilder canvas = baseCanvas(menus, "SkyBlock Menu");
        canvas.place(13, stats)
                .place(19, simpleButton("diamond_sword", "Your Skills"))
                .place(20, simpleButton("painting", "Collections"))
                .place(24, simpleButton("clock", "Calendar and Events"))
                .place(47, simpleButton("player_head", "Fast Travel"))
                .place(46, simpleButton("name_tag", "Profile Management"))
                .place(51, simpleButton("cookie", "Booster Cookie"));
        return canvas.build();
    }

    private static Menu bazaarOddities(MenuService menus) {
        MenuButton cookie = MenuButton.builder(MenuIcon.vanilla("cookie"))
                .name(gold("Booster Cookie"))
                .secondary("1 product")
                .section(section -> section
                        .valueLine(gray("Buy price: "), gold("12,683,012 coins"))
                        .mutedLine("5.9k in 409 offers")
                        .mutedLine("90.5k insta-buys in 7d"))
                .section(section -> section
                        .valueLine(gray("Sell price: "), gold("12,463,080 coins"))
                        .mutedLine("20.5k in 1.2k orders")
                        .mutedLine("77.9k insta-sells in 7d"))
                .onLeftClick(ActionVerb.VIEW, "view product", context -> { })
                .build();
        return baseCanvas(menus, "Bazaar > Oddities")
                .place(11, cookie)
                .place(13, simpleButton("enchanting_table", "Enchantments"))
                .place(21, simpleButton("paper", "Stock of Stonks"))
                .place(36, simpleButton("enchanting_table", "Oddities"))
                .build();
    }

    private static Menu boosterCookie(MenuService menus) {
        MenuDisplayItem detail = MenuDisplayItem.builder(MenuIcon.vanilla("cookie"))
                .name(gold("Booster Cookie"))
                .description("Consume to gain the Cookie Buff for 4 days:")
                .bullets(
                        "Ability to gain Bits!",
                        "+25 Wisdom on all Wisdom stats",
                        "+15 Magic Find",
                        "Keep coins on death",
                        "Permafly on private islands and gardens",
                        "Quick access to utility menus using their commands",
                        "AFK immunity on your island and garden",
                        "Increases Chocolate Factory production by +0.25x")
                .status(Component.text("LEGENDARY", NamedTextColor.GOLD))
                .glow()
                .build();
        return baseCanvas(menus, "Oddities > Booster Cookie")
                .rows(4)
                .place(10, actionButton("golden_horse_armor", "Buy Instantly", ActionVerb.BUY))
                .place(11, actionButton("hopper", "Sell Instantly", ActionVerb.MANAGE, "sell instantly"))
                .place(13, detail)
                .place(15, actionButton("filled_map", "Create Buy Order", ActionVerb.BUY))
                .place(16, actionButton("map", "Create Sell Offer", ActionVerb.MANAGE, "create sell offer"))
                .build();
    }

    private static Menu bazaarOrders(MenuService menus) {
        MenuButton order = MenuButton.builder(MenuIcon.vanilla("enchanted_book"))
                .name(gold("SELL Bank V"))
                .secondary("Worth 386.1k coins")
                .valueLine("Offer amount: ", gold("2x"))
                .status(red("Expired!"))
                .valueLine("Price per unit: ", gold("194,999.5 coins"))
                .onLeftClick(ActionVerb.VIEW, "view options", context -> { })
                .build();
        return baseCanvas(menus, "Your Bazaar Orders")
                .place(10, order)
                .place(19, simpleButton("enchanted_book", "BUY Infinite Quiver VI"))
                .place(32, actionButton("hopper", "Claim All Coins", ActionVerb.CLAIM))
                .build();
    }

    private static Menu tasksCore(MenuService menus) {
        MenuDisplayItem tasks = MenuDisplayItem.builder(MenuIcon.vanilla("nether_star"))
                .name(green("Core Tasks"))
                .secondary("9 Tasks")
                .description("All of the core XP tasks that are integral to your general progression in SkyBlock!")
                .bullets(
                        "Skill Level Up",
                        "Museum Donations",
                        "Fairy Souls",
                        "Accessory Bag",
                        "Pet Score",
                        "Collections",
                        "Craft Minions",
                        "Bank Upgrades",
                        "Fast Travels Unlocked")
                .progress(MenuProgress.builder("Progress to Complete Category", 10_428, 18_826)
                        .unit("XP")
                        .build())
                .build();
        return baseCanvas(menus, "Tasks > Core")
                .place(4, tasks)
                .place(20, simpleButton("diamond_sword", "Skill Level Up"))
                .place(21, simpleButton("gold_block", "Museum Donations"))
                .place(29, simpleButton("painting", "Collections"))
                .place(32, simpleButton("player_head", "Bank Upgrades"))
                .place(33, simpleButton("map", "Fast Travels Unlocked"))
                .build();
    }

    private static Menu skills(MenuService menus) {
        MenuButton farming = MenuButton.builder(MenuIcon.vanilla("golden_hoe"))
                .name(gold("Farming LI"))
                .description("Harvest crops and shear sheep to earn Farming EXP!")
                .progress("Progress to Level LII", 17_293_845.9, 4_600_000)
                .section(section -> section
                        .line(gold("Level LII Rewards:"))
                        .bullets(
                                "Farmhand LII",
                                "+5 Health",
                                "+1,000,000 Coins",
                                "+30 SkyBlock XP"))
                .mutedLine("Increase your Farming level cap by visiting Jacob and Anita in the Hub or Garden!")
                .onLeftClick(ActionVerb.VIEW, context -> { })
                .build();
        return baseCanvas(menus, "Your Skills")
                .place(20, farming)
                .place(19, simpleButton("stone_sword", "Combat LVI"))
                .place(21, simpleButton("fishing_rod", "Fishing XL"))
                .place(22, simpleButton("stone_pickaxe", "Mining LI"))
                .place(23, simpleButton("jungle_sapling", "Foraging XXXV"))
                .build();
    }

    private static Menu collections(MenuService menus) {
        MenuButton farming = MenuButton.builder(MenuIcon.vanilla("golden_hoe"))
                .name(gold("Farming Collections"))
                .description("View your Farming Collections!")
                .progress("Collections Unlocked", 18, 20)
                .onLeftClick(ActionVerb.VIEW, context -> { })
                .build();
        return baseCanvas(menus, "Collections")
                .place(20, farming)
                .place(21, simpleButton("stone_pickaxe", "Mining Collections"))
                .place(22, simpleButton("stone_sword", "Combat Collections"))
                .place(23, simpleButton("jungle_sapling", "Foraging Collections"))
                .place(24, simpleButton("fishing_rod", "Fishing Collections"))
                .build();
    }

    private static Menu bestiary(MenuService menus) {
        MenuDisplayItem info = MenuDisplayItem.builder(MenuIcon.vanilla("writable_book"))
                .name(green("Bestiary"))
                .description("The Bestiary is a compendium of mobs in SkyBlock. It contains detailed information on loot drops, your mob stats, and more!")
                .description("Kill mobs within Families to progress and earn rewards, including Magic Find bonuses towards mobs in the Family.")
                .progress("Families Found", 155, 268)
                .progress("Families Completed", 22, 268)
                .build();
        return baseCanvas(menus, "Bestiary")
                .place(4, info)
                .place(10, simpleButton("player_head", "Your Island"))
                .place(11, simpleButton("player_head", "Hub"))
                .place(20, simpleButton("player_head", "Dwarven Mines"))
                .place(23, simpleButton("player_head", "Galatea"))
                .place(51, simpleButton("player_head", "Bestiary Milestone CL"))
                .build();
    }

    private static Menu communityShop(MenuService menus) {
        MenuButton cookie = MenuButton.builder(MenuIcon.vanilla("cookie"))
                .name(gold("Booster Cookie"))
                .description(Component.text()
                        .append(gray("Obtain a temporary buff letting you earn "))
                        .append(aqua("bits"))
                        .append(gray(", as well as "))
                        .append(Component.text("tons of perks", NamedTextColor.LIGHT_PURPLE))
                        .append(gray(".")))
                .glow()
                .onLeftClick(ActionVerb.VIEW, context -> { })
                .build();
        return baseCanvas(menus, "Community Shop")
                .place(1, simpleButton("golden_horse_armor", "City Projects"))
                .place(2, simpleButton("hopper", "Account & Profile Upgrades"))
                .place(3, cookie)
                .place(4, simpleButton("diamond", "Bits Shop"))
                .build();
    }

    private static Menu heartOfForest(MenuService menus) {
        MenuDisplayItem info = MenuDisplayItem.builder(MenuIcon.vanilla("player_head"))
                .name(green("Heart of the Forest"))
                .valueLine("Tokens of the Forest: ", green("1"))
                .description("Use Tokens of the Forest to unlock perks and abilities above!")
                .section(section -> section
                        .line(gold("Whispers"))
                        .paragraph("Whispers are dropped from cutting Logs on Galatea, and are used to upgrade the perks you've unlocked!")
                        .valueLine("Forest Whispers: ", aqua("0")))
                .build();
        return baseCanvas(menus, "Heart of the Forest")
                .place(4, simpleButton("paper", "Center of the Forest"))
                .place(20, simpleButton("paper", "Luck of the Forest"))
                .place(31, simpleButton("paper", "Foraging Fortune"))
                .place(47, simpleButton("chest", "Heart of the Forest Slot"))
                .place(40, info)
                .build();
    }

    private static Menu fastTravel(MenuService menus) {
        MenuButton hub = MenuButton.builder(MenuIcon.vanilla("player_head"))
                .name(green("SkyBlock Hub"))
                .secondary("/warp hub")
                .description("Where everything happens and anything is possible.")
                .onLeftClick(ActionVerb.OPEN, context -> { })
                .onRightClick(ActionVerb.OPEN, "warp", context -> { })
                .build();
        return baseCanvas(menus, "Fast Travel")
                .place(10, simpleButton("player_head", "Private Island"))
                .place(11, hub)
                .place(12, simpleButton("player_head", "Dungeon Hub - Spawn"))
                .place(13, simpleButton("player_head", "The Barn - Spawn"))
                .place(33, simpleButton("player_head", "Warp to: Jerry's Workshop"))
                .build();
    }

    private static Menu profileManagement(MenuService menus) {
        MenuButton slot = MenuButton.builder(MenuIcon.vanilla("bedrock"))
                .name(red("Profile Slot #5"))
                .secondary("Unavailable")
                .section(section -> section
                        .line(gold("Cost"))
                        .line(Component.text("2,750 SkyBlock Gems", NamedTextColor.AQUA)))
                .valueLine("You have: ", aqua("1,760 Gems"))
                .status(red("Cannot afford this!"))
                .onLeftClick(ActionVerb.BUY, "get gems", context -> { })
                .build();
        return baseCanvas(menus, "Profile Management")
                .rows(4)
                .place(11, simpleButton("emerald_block", "Profile: Cucumber"))
                .place(12, simpleButton("grass_block", "Profile: Apple"))
                .place(13, simpleButton("oak_button", "Empty Profile Slot"))
                .place(14, simpleButton("oak_button", "Empty Profile Slot"))
                .place(15, slot)
                .build();
    }

    private static Menu calendar(MenuService menus) {
        MenuDisplayItem feast = MenuDisplayItem.builder(MenuIcon.vanilla("golden_hoe"))
                .name(gold("19th Harvest Feast"))
                .section(section -> section
                        .valueLine("Starts in: ", green("2d 1h 6m 55s"))
                        .valueLine("Event lasts for ", green("1d 7h!")))
                .description("Farm in-season crops to help season the Communal Stew. Talk to Feast Chef Ted in the Hub for more info.")
                .build();
        return baseCanvas(menus, "Calendar and Events")
                .place(10, simpleButton("jukebox", "417th Election Over!"))
                .place(11, simpleButton("player_head", "Traveling Zoo"))
                .place(13, feast)
                .place(14, simpleButton("jack_o_lantern", "505th Spooky Festival"))
                .place(36, simpleButton("gold_block", "Event Rewards"))
                .build();
    }

    private static Menu museumMilestones(MenuService menus) {
        MenuDisplayItem info = MenuDisplayItem.builder(MenuIcon.vanilla("gold_block"))
                .name(gold("Museum Milestones"))
                .description("Every 100 SkyBlock XP obtained from your Museum, Eleanor will reward you.")
                .mutedLine("Special Items do not reward SkyBlock XP.")
                .section(section -> section
                        .valueLine("Total XP: ", gold("329/3,583"))
                        .valueLine("Milestone: ", green("3/40")))
                .progress("Progress to Milestone 4", 29, 100)
                .build();
        return baseCanvas(menus, "Museum Milestones").place(27, info).build();
    }

    private static Menu chocolateMilestones(MenuService menus) {
        MenuDisplayItem info = MenuDisplayItem.builder(MenuIcon.vanilla("ladder"))
                .name(gold("Chocolate Factory Milestones"))
                .description("Unlock special Chocolate Rabbits by reaching all-time Chocolate milestones!")
                .status(Component.text("You have 9 unclaimed rewards!", NamedTextColor.YELLOW))
                .build();
        return baseCanvas(menus, "Chocolate Factory Milestones").place(27, info).build();
    }

    private static Menu statsTuning(MenuService menus) {
        MenuDisplayItem info = MenuDisplayItem.builder(MenuIcon.vanilla("comparator"))
                .name(green("Stats Tuning"))
                .description("Optimize your build to your liking by using Tuning Points.")
                .mutedLine("Every 10 Accessory Power grants 1 Tuning Point.")
                .section(section -> section
                        .valueLine("Accessory Power: ", gold("131"))
                        .valueLine("Tuning Points: ", green("13")))
                .section(section -> section
                        .line(gold("Your tuning:"))
                        .valueLine("", aqua("+19.5 Speed")))
                .build();
        return baseCanvas(menus, "Stats Tuning")
                .rows(5)
                .place(4, info)
                .place(19, simpleButton("golden_apple", "Health"))
                .place(20, simpleButton("iron_chestplate", "Defense"))
                .place(21, simpleButton("sugar", "Speed"))
                .place(22, simpleButton("blaze_powder", "Strength"))
                .place(28, simpleButton("player_head", "Crit Damage"))
                .build();
    }

    private static Menu personalBank(MenuService menus) {
        MenuDisplayItem info = MenuDisplayItem.builder(MenuIcon.vanilla("redstone_torch"))
                .name(gold("Information"))
                .description("Keep your coins safe in the bank! You lose half the coins in your purse when dying in combat.")
                .valueLine("Balance limit: ", gold("100 Million"))
                .description("The banker rewards you every 31 hours with interest for the coins in your bank balance.")
                .section(section -> section
                        .valueLine("Interest in: ", green("18h"))
                        .valueLine("Last interest: ", gold("318,000 coins"))
                        .valueLine("Projected: ", gold("318,000 coins (0.34%)")))
                .section(section -> section
                        .line(gold("Interest Tranches"))
                        .mutedLine("First 10M coins yields 2.12% interest.")
                        .mutedLine("From 10M to 20M coins yields 1.06% interest.")
                        .valueLine("Max interest: ", gold("318,000")))
                .build();
        return baseCanvas(menus, "Personal Bank Account")
                .rows(4)
                .place(11, actionButton("chest", "Deposit Coins", ActionVerb.MANAGE, "deposit coins"))
                .place(13, actionButton("dropper", "Withdraw Coins", ActionVerb.MANAGE, "withdraw coins"))
                .place(15, simpleButton("filled_map", "Recent transactions"))
                .place(32, info)
                .place(34, simpleButton("gold_block", "Bank Upgrades"))
                .build();
    }

    private static Menu bankUpgrades(MenuService menus) {
        MenuDisplayItem goldUpgrade = MenuDisplayItem.builder(MenuIcon.vanilla("gold_nugget"))
                .name(gold("Gold Bank Upgrade"))
                .secondary("XP Task")
                .description("Upgrade your Bank Account to this level to gain +20 XP.")
                .checklist(MenuChecklistEntry.complete(
                        Component.text("You have completed this task!", NamedTextColor.GREEN)))
                .build();
        return baseCanvas(menus, "Core > Bank Upgrades")
                .place(4, simpleButton("player_head", "Bank Upgrades"))
                .place(19, goldUpgrade)
                .place(20, simpleButton("gold_ingot", "Deluxe Bank Upgrade"))
                .place(21, simpleButton("golden_chestplate", "Super Deluxe Bank Upgrade"))
                .place(23, simpleButton("golden_horse_armor", "Premier Bank Upgrade"))
                .build();
    }

    private static Menu supercraft(MenuService menus) {
        MenuButton craft = MenuButton.builder(MenuIcon.vanilla("golden_pickaxe"))
                .name(green("Supercraft"))
                .checklist(
                        MenuChecklistEntry.incomplete(Component.text()
                                .append(red("0/1 "))
                                .append(Component.text("Crystal Fragment", NamedTextColor.DARK_PURPLE))),
                        MenuChecklistEntry.incomplete(Component.text()
                                .append(red("0/8 "))
                                .append(Component.text("End Stone", NamedTextColor.WHITE))))
                .description("Ingredients are taken from your inventory, sacks, ender chests and backpacks.")
                .status(red("Missing ingredients!"))
                .onRightClick(ActionVerb.SELECT, "set amount", context -> { })
                .build();
        return baseCanvas(menus, "Catalyst").place(32, craft).build();
    }

    private static Menu confirmation(MenuService menus) {
        MenuDisplayItem info = MenuDisplayItem.builder(MenuIcon.vanilla("hopper"))
                .name(gold("Selling whole inventory"))
                .section(section -> section
                        .valueLine("You sell: ", gold("2x products"))
                        .valueLine("You earn: ", gold("49.5 coins")))
                .build();
        MenuButton cancel = MenuButton.builder(MenuIcon.vanilla("red_terracotta"))
                .name(red("Cancel"))
                .onLeftClick(ActionVerb.BACK, "cancel", context -> context.back())
                .build();
        MenuButton confirm = MenuButton.builder(MenuIcon.vanilla("green_terracotta"))
                .name(green("Confirm"))
                .onLeftClick(ActionVerb.CONFIRM, context -> { })
                .build();
        return menus.confirmation()
                .title("Are you sure?")
                .info(info)
                .cancel(cancel)
                .confirm(confirm)
                .build();
    }

    private static CanvasMenuBuilder baseCanvas(MenuService menus, String title) {
        return menus.canvas().title(title).rows(6);
    }

    private static MenuButton simpleButton(String icon, String name) {
        return MenuButton.builder(MenuIcon.vanilla(icon))
                .name(green(name))
                .onLeftClick(ActionVerb.VIEW, context -> { })
                .build();
    }

    private static MenuButton actionButton(String icon, String name, ActionVerb verb) {
        return MenuButton.builder(MenuIcon.vanilla(icon))
                .name(green(name))
                .onLeftClick(verb, context -> { })
                .build();
    }

    private static MenuButton actionButton(String icon, String name, ActionVerb verb, String prompt) {
        return MenuButton.builder(MenuIcon.vanilla(icon))
                .name(green(name))
                .onLeftClick(verb, prompt, context -> { })
                .build();
    }

    private static ShowcaseEntry golden(
            String id,
            String label,
            String surfaceSha,
            String itemSha,
            int slot,
            Menu menu,
            ShowcaseFeature... features
    ) {
        return new ShowcaseEntry(
                id,
                label,
                ShowcaseOrigin.CORPUS_GOLDEN,
                Optional.of(SourceReference.of(surfaceSha, itemSha, slot)),
                menu,
                List.of(new ShowcaseSnapshot("normalized", menu)),
                Set.of(features));
    }

    private static Component gray(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private static Component green(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    private static Component gold(String text) {
        return Component.text(text, NamedTextColor.GOLD);
    }

    private static Component aqua(String text) {
        return Component.text(text, NamedTextColor.AQUA);
    }

    private static Component blue(String text) {
        return Component.text(text, NamedTextColor.BLUE);
    }

    private static Component red(String text) {
        return Component.text(text, NamedTextColor.RED);
    }
}
