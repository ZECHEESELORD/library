package sh.harold.library.example.minestom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.entity.Player;
import sh.harold.library.menu.ActionVerb;
import sh.harold.library.menu.Menu;
import sh.harold.library.menu.MenuButton;
import sh.harold.library.menu.MenuContext;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.MenuService;
import sh.harold.library.menu.MenuTab;
import sh.harold.library.menu.core.StandardMenuService;
import sh.harold.library.message.Click;
import sh.harold.library.message.InlineMessage;
import sh.harold.library.message.Message;
import sh.harold.library.message.MessageBlock;
import sh.harold.library.message.MessageValue;
import sh.harold.library.message.Tag;
import sh.harold.library.message.TitleMessage;
import sh.harold.library.message.Topic;
import sh.harold.library.message.minestom.MinestomMessageSender;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

final class MinestomMessageShowcase {

    static final String SCREENSHOT_SHA256 = "F4B2EEC793FCCCFFBA11D2CE696B1F6F1A5DD1A42839C90C2D439F76E2348637";
    static final String DIVIDER = "\u25ac".repeat(64);

    private static final Topic COLLECTION = Topic.of("COLLECTION!", NamedTextColor.GOLD);

    private final MenuService menus;
    private final MinestomMessageSender sender;
    private final List<Entry> goldens;
    private final List<Entry> synthesized;
    private final List<Entry> entries;

    MinestomMessageShowcase() {
        this(new StandardMenuService(), new MinestomMessageSender());
    }

    MinestomMessageShowcase(MenuService menus, MinestomMessageSender sender) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.goldens = createGoldens();
        this.synthesized = createSynthesized();
        this.entries = Stream.concat(goldens.stream(), synthesized.stream()).toList();
        if (entries.stream().map(Entry::id).distinct().count() != entries.size()) {
            throw new IllegalStateException("Message showcase entry ids must be unique");
        }
    }

    List<Entry> goldens() {
        return goldens;
    }

    List<Entry> synthesized() {
        return synthesized;
    }

    List<Entry> entries() {
        return entries;
    }

    Menu panel(Player player) {
        Objects.requireNonNull(player, "player");
        return panel(entry -> entry.outputs().forEach(output -> output.send(sender, player)));
    }

    Menu panel(Consumer<Entry> selection) {
        Objects.requireNonNull(selection, "selection");
        return menus.tabs()
                .title("Message Showcase")
                .defaultTab("goldens")
                .addTab(MenuTab.builder("goldens", MenuIcon.vanilla("nether_star"))
                        .name(Component.text("Corpus Goldens", NamedTextColor.GOLD))
                        .secondary("9 observed messages")
                        .items(goldens.stream().map(entry -> entryButton(entry, selection)).toList())
                        .build())
                .addTab(MenuTab.builder("synthesized", MenuIcon.vanilla("crafting_table"))
                        .name(Component.text("Synthesized", NamedTextColor.AQUA))
                        .secondary("13 house-style messages")
                        .items(synthesized.stream().map(entry -> entryButton(entry, selection)).toList())
                        .build())
                .build();
    }

    void sendChatMenuPage(Player player, int page) {
        sender.send(player, chatMenuPage(page));
    }

    static void dispatch(MenuContext context, Runnable delivery) {
        Objects.requireNonNull(context, "context").close();
        Objects.requireNonNull(delivery, "delivery").run();
    }

    private MenuButton entryButton(Entry entry, Consumer<Entry> selection) {
        NamedTextColor color = entry.origin() == Origin.CORPUS_GOLDEN
                ? NamedTextColor.GOLD
                : NamedTextColor.AQUA;
        return MenuButton.builder(MenuIcon.vanilla(entry.icon()))
                .name(Component.text(entry.label(), color))
                .secondary(entry.description())
                .onLeftClick(ActionVerb.VIEW, "send", context ->
                        dispatch(context, () -> selection.accept(entry)))
                .build();
    }

    private static List<Entry> createGoldens() {
        return List.of(
                bankInterest(),
                questUnlocks(),
                areaDiscovery(),
                questComplete(),
                taskComplete(),
                materialStash(),
                riftWarning(),
                treeGift(),
                bonusTreeGift()
        );
    }

    private static Entry bankInterest() {
        InlineMessage message = Message.success(
                "You have just received {amount} as interest in your personal bank account!",
                Message.slot("amount", Message.value("636,000 coins").color(NamedTextColor.GOLD))
        );
        return golden(
                "bank-interest",
                "Bank Interest",
                "gold_ingot",
                "Personal bank interest payout.",
                features(Feature.NOTICE_SUCCESS, Feature.SEMANTIC_UI_VALUES),
                source("Corpus chat message", null,
                        "ac3739f7dfa58597df5477be9abab8acf43d05a40100ac4415d330916186ecd2"),
                new ChatOutput(message)
        );
    }

    private static Entry questUnlocks() {
        MessageBlock questLogHover = coloredHover("Click to view your Quest Log!", NamedTextColor.YELLOW);
        MessageValue swoop = Message.value(unlockComponent("Swoop's Instructions"))
                .click(Click.runCommand("/questlog"))
                .hover(questLogHover);
        MessageValue david = Message.value(unlockComponent("David's Hunting Lessons"))
                .click(Click.runCommand("/questlog"))
                .hover(questLogHover);
        MessageValue prompt = Message.value(Component.text(
                        "Open your Quest Log to find out more!",
                        NamedTextColor.GREEN,
                        TextDecoration.BOLD
                ))
                .click(Click.runCommand("/questlog"))
                .hover(questLogHover);
        MessageBlock block = Message.block()
                .line("{swoop}", Message.slot("swoop", swoop))
                .line("{david}", Message.slot("david", david))
                .line("{prompt}", Message.slot("prompt", prompt))
                .build();
        return golden(
                "quest-unlocks",
                "Quest Unlocks",
                "book",
                "Linked Quest Log announcements.",
                features(Feature.BLOCK, Feature.CLICK_RUN_COMMAND, Feature.VALUE_HOVER),
                source("Corpus tick-2 chat burst", null,
                        "a2824cd5533a581715f3cea1388e549a5a0dfe82299d01ef4f61722fcd5249ab",
                        "17747e59f045a9d24819d05e601a9a8cf1372bb44301fbcea930d34e3ec315ae",
                        "06a105e01dd798b84caaf8244cad9e8242817450ae71195218a1b451a377fd4d"),
                new BlockOutput(block)
        );
    }

    private static Entry areaDiscovery() {
        TitleMessage title = Message.title(
                Component.text("Tangleburg's Path", NamedTextColor.AQUA),
                Component.text("NEW AREA DISCOVERED!", NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        MessageBlock block = Message.block()
                .title("NEW AREA DISCOVERED!", NamedTextColor.GOLD)
                .line(
                        "{area}",
                        Message.slot("area", Message.value(Component.text("Tangleburg's Path", NamedTextColor.AQUA)))
                )
                .bullet("{task}", Message.slot("task", areaTask("Talk to ", "Hina", NamedTextColor.AQUA, " about Heart of the Forest!")))
                .bullet("{task}", Message.slot("task", areaTask("Talk to ", "Swoop", NamedTextColor.AQUA, " about Tree Cutting!")))
                .bullet("{task}", Message.slot("task", areaTask("Talk to ", "David Hunterborough", NamedTextColor.AQUA, " about Hunting!")))
                .bullet("{task}", Message.slot("task", areaTask("Compete in ", "Starlyn Contests", NamedTextColor.LIGHT_PURPLE, "!")))
                .build();
        return golden(
                "area-discovery",
                "Area Discovery",
                "compass",
                "Title and local objectives.",
                features(Feature.TITLE_SUBTITLE, Feature.BLOCK, Feature.BULLETS),
                source("Corpus tick-12 title and chat burst", null,
                        "7d63b379314f15928fdfd0e98dd25626d88d6d04ef40aba95a77f768461c1973",
                        "e07be350e430f2300814534772b3e274d539ab163b4fc0533ab731b2fbaf9a6b",
                        "34156d7c96c0e8a3c7d17f809cf5b8d3ebe2b24e75be59f514f27f3b1a6349df",
                        "cf01a2cb22fb7990898750263e93c371d069dd4be4d1226f1d103008b8cebe97",
                        "ca8f0c54ed6e4dcc29b85e747c356bd8be89b44ff6e966b874d6e46bcc67a295",
                        "5ba810d29a6f85e3967d7c62c7e46a8ac594ece04d487e0771aa8a1c3a60779a",
                        "390f8f104fc61f8031f92e1267c9ec7f96bc9a7db9f5ca8f68e558dc0e933b7e",
                        "32e13ee4258c674807da6ad33701e59184288b54f2f783b97e0ab388d90facdb"),
                new TitleOutput(title),
                new BlockOutput(block)
        );
    }

    private static Entry questComplete() {
        MessageValue heading = Message.value(Component.text(
                        "QUEST COMPLETE",
                        NamedTextColor.GOLD,
                        TextDecoration.BOLD
                ))
                .click(Click.runCommand("/questlog"))
                .hover(questCompleteHover());
        MessageValue coins = Message.value(rewardComponent(
                        "10,000",
                        NamedTextColor.GOLD,
                        " Coins"
                ))
                .hover(coinsRewardHover());
        MessageValue foraging = Message.value(rewardComponent(
                        "5,000",
                        NamedTextColor.DARK_AQUA,
                        " Foraging Experience"
                ))
                .hover(rewardHover("5,000", NamedTextColor.DARK_AQUA, " Foraging Experience"));
        MessageValue skyBlockXp = Message.value(rewardComponent(
                        "5",
                        NamedTextColor.AQUA,
                        " SkyBlock XP"
                ))
                .click(Click.runCommand("/levels"))
                .hover(rewardHover("5", NamedTextColor.AQUA, " SkyBlock XP"));
        MessageBlock block = Message.block()
                .line("{heading}", Message.slot("heading", heading))
                .line("{rewards}", Message.slot("rewards", greenBold("REWARDS")))
                .line("{reward}", Message.slot("reward", coins))
                .line("{reward}", Message.slot("reward", foraging))
                .line("{reward}", Message.slot("reward", skyBlockXp))
                .build();
        return golden(
                "quest-complete",
                "Quest Complete",
                "writable_book",
                "Quest rewards and hover detail.",
                features(Feature.BLOCK, Feature.CLICK_RUN_COMMAND, Feature.VALUE_HOVER),
                source("Corpus tick-5483 chat burst", null,
                        "a2da72c21a03798d0fb6b3279b7614ecac7d31b80761ac7a3772405394da43b0",
                        "98676ccb19b532e97a9d8323f37fe129b900272b2e4d0e63ee85382af28a85fb",
                        "5ca046b1badcec8edc7620572843d8395865c96b736949016ef46d9288ca7f32",
                        "49512dfc1b2761cd8f7e098827baf4c03a31b23844eda07075767628ac325b73",
                        "2e1b2dd002e33bb43f890430d836e9045d544e17ed16b29d1489d6b2cee4b2dc"),
                new BlockOutput(block)
        );
    }

    private static Entry taskComplete() {
        MessageBlock taskHover = coloredHover("Click view tasks!", NamedTextColor.YELLOW);
        MessageValue heading = Message.value(Component.text(
                        "TASK COMPLETE",
                        NamedTextColor.AQUA,
                        TextDecoration.BOLD
                ))
                .click(Click.runCommand("/chapters moonglade"))
                .hover(taskHover);
        MessageValue chapter = Message.value(Component.text()
                        .append(Component.text("Moonglade Marsh", NamedTextColor.DARK_GREEN))
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text("Chapter I", NamedTextColor.WHITE))
                        .build())
                .click(Click.runCommand("/chapters moonglade"))
                .hover(taskHover);
        MessageValue foraging = Message.value(rewardComponent(
                        "100",
                        NamedTextColor.DARK_AQUA,
                        " Foraging Experience"
                ))
                .hover(rewardHover("100", NamedTextColor.DARK_AQUA, " Foraging Experience"));
        MessageValue claim = Message.value(Component.text(
                        "CLICK TO CLAIM",
                        NamedTextColor.GOLD,
                        TextDecoration.BOLD
                ))
                .click(Click.runCommand("/chapters moonglade"))
                .hover(taskHover);
        MessageBlock block = Message.block()
                .line("{heading}", Message.slot("heading", heading))
                .line("{chapter}", Message.slot("chapter", chapter))
                .line("Talk to Swoop")
                .line("{reward}", Message.slot("reward", greenBold("REWARD")))
                .line("{reward}", Message.slot("reward", foraging))
                .line("{claim}", Message.slot("claim", claim))
                .build();
        return golden(
                "task-complete",
                "Task Complete",
                "paper",
                "Chapter task and claim action.",
                features(Feature.BLOCK, Feature.CLICK_RUN_COMMAND, Feature.VALUE_HOVER),
                source("Corpus tick-5500 chat burst", null,
                        "aa1420ef8f289bf18c0d0035580158195bc5404d773432f81c37da534923f102",
                        "198e1701aba290a4be36c85c017da8140fc20629014aa47b9987523089d04e84",
                        "beaaac81c06078f3a34b7aa9b827c025a55d65a0fc03b688cbdf18f01d1cb804",
                        "5c75bf2889c5d907c2d5499eec3fcc62bd7d99aaa2fe52c1806c0f1bd227d1b3",
                        "7888181330b0456005faf610d49b643b59729f97ce70d4d1a3ac35d5c5603792",
                        "03eda5cd7431e5e6c6581a453df3137aed88595eb038cabf68b4dd3b19b7fa74"),
                new BlockOutput(block)
        );
    }

    private static Entry materialStash() {
        MessageBlock hover = coloredHover("Click to pickup your materials!", NamedTextColor.YELLOW);
        MessageValue summary = materialStashLine(Component.text()
                .append(Component.text("You have ", NamedTextColor.GRAY))
                .append(Component.text("53", NamedTextColor.DARK_AQUA))
                .append(Component.text(" materials stashed away!", NamedTextColor.GRAY))
                .build(), hover);
        MessageValue metadata = materialStashLine(
                Component.text("(This totals 32 types of materials stashed!)", NamedTextColor.DARK_GRAY),
                hover
        );
        MessageValue pickup = materialStashLine(Component.text()
                .append(Component.text(">>> ", NamedTextColor.DARK_AQUA, TextDecoration.BOLD))
                .append(Component.text("CLICK HERE", NamedTextColor.DARK_AQUA, TextDecoration.BOLD))
                .append(Component.text(" to pick them up! ", NamedTextColor.AQUA))
                .append(Component.text("<<<", NamedTextColor.DARK_AQUA, TextDecoration.BOLD))
                .build(), hover);
        MessageBlock block = Message.centeredBlock()
                .line("{summary}", Message.slot("summary", summary))
                .line("{metadata}", Message.slot("metadata", metadata))
                .line("{pickup}", Message.slot("pickup", pickup))
                .build();
        return golden(
                "material-stash",
                "Material Stash",
                "chest",
                "Centered stash summary and pickup.",
                features(Feature.BLOCK, Feature.CENTERED_BLOCK, Feature.CLICK_RUN_COMMAND, Feature.VALUE_HOVER),
                source("Corpus tick-96 chat burst", null,
                        "a8e6ffdcf30567028bf8f0fec8bd7115b83c5b08c2090a81f1880437eceaf63c",
                        "58ea2dd60175c76b20b2ed3696c71154e5c3370f5cc7923632773305bb26f649",
                        "0ad2a5f5a390092998a9c667907dc69557eb89eb9115d017e7053587eb38a2ba"),
                new BlockOutput(block)
        );
    }

    private static Entry riftWarning() {
        MessageBlock block = Message.block()
                .title(DIVIDER, NamedTextColor.DARK_PURPLE)
                .title("RIFT INSTABILITY WARNING", NamedTextColor.LIGHT_PURPLE)
                .line(
                        "Venturing in the {rift} breaches spacetime.",
                        Message.slot("rift", Message.value("Rift").color(NamedTextColor.LIGHT_PURPLE))
                )
                .line(
                        "You have {time} left before the rift collapses!",
                        Message.slot("time", Message.value("8m00s").color(NamedTextColor.GREEN))
                )
                .line(
                        "{note}",
                        Message.slot("note", Message.value(
                                "Your dimensional infusion has been consumed!"
                        ).color(NamedTextColor.DARK_GRAY))
                )
                .title(DIVIDER, NamedTextColor.DARK_PURPLE)
                .build();
        return golden(
                "rift-warning",
                "Rift Warning",
                "ender_eye",
                "Divider-framed instability warning.",
                features(Feature.BLOCK, Feature.BLOCK_SPACING),
                source("Corpus tick-535 chat burst; closing divider normalized from the opening frame", null,
                        "6fd5af1e326682b24ce6541f5fd537db6f07297aea40ec1b82a9f513c0875e39",
                        "2ba8e24df43beb36f05d3e33611028b1292a34b660f8229129e6ac5a9b8f396d",
                        "0bf3ec1cee4c6057ff8dc6816511eb4986a1f86c71c8df99a7f6ff51178d2ab4",
                        "d06a2b899f7b627612c6abce6448c795bf3b9a15548fb10b9f5500f53b762ca7",
                        "bed3ce91d15b40dac47f695564b615ae6edcc8e55d31f48487af5b508f8a61b9"),
                new BlockOutput(block)
        );
    }

    private static Entry treeGift() {
        MessageBlock rewards = treeGiftRewardsHover();
        MessageValue rewardLine = treeGiftRewardLine(rewards);
        MessageBlock block = Message.centeredBlock()
                .title(DIVIDER, NamedTextColor.DARK_GREEN)
                .title("TREE GIFT", NamedTextColor.DARK_GREEN)
                .line(
                        "You helped cut {progress} of the {tree}.",
                        Message.slot("progress", Message.value("100%").color(NamedTextColor.GREEN)),
                        Message.slot("tree", Message.value("Fig Tree").color(NamedTextColor.GREEN))
                )
                .line("{reward}", Message.slot("reward", rewardLine))
                .build();
        return golden(
                "tree-gift",
                "Tree Gift",
                "oak_sapling",
                "Centered tree contribution reward.",
                features(Feature.BLOCK, Feature.CENTERED_BLOCK, Feature.VALUE_HOVER),
                source("Corpus tick-4980 chat burst", null,
                        "7f6ea55df56d8705f2d2ca7644aa005992b6fde8d33a854c855025e9124ea26c",
                        "b1b7757d49320bad5667c0fe5cb8ddb0dbe0222cb158b0536fda27567d273173",
                        "39fa51d8ab8bb4931f65f03781a9aa28c6a0ace457dd845d085e35fa98f128aa",
                        "cff9d1c558e05eb5731448d755f6651c5f69be6090d193c109476a0a9e009c8a"),
                new BlockOutput(block)
        );
    }

    private static Entry bonusTreeGift() {
        MessageBlock rewards = treeGiftRewardsHover();
        MessageValue rewardLine = treeGiftRewardLine(rewards);
        MessageBlock block = Message.centeredBlock()
                .title(DIVIDER, NamedTextColor.DARK_GREEN)
                .title("TREE GIFT", NamedTextColor.DARK_GREEN)
                .line(
                        "You helped cut {progress} of the {tree}.",
                        Message.slot("progress", Message.value("100%").color(NamedTextColor.GREEN)),
                        Message.slot("tree", Message.value("Fig Tree").color(NamedTextColor.GREEN))
                )
                .line("{reward}", Message.slot("reward", rewardLine))
                .blank()
                .title("BONUS GIFT", NamedTextColor.LIGHT_PURPLE)
                .line("{drop} {chance}",
                        Message.slot("drop", white("Sweep Booster")),
                        Message.slot("chance", green("(0.5%)")))
                .line("{drop} {chance}",
                        Message.slot("drop", gold("Chameleon Shard")),
                        Message.slot("chance", green("(0.08%)")))
                .line("{drop} {chance}",
                        Message.slot("drop", red("Tree the Fish")),
                        Message.slot("chance", green("(0.05%)")))
                .line("A {drop} fell from the Tree!",
                        Message.slot("drop", purple("Phanflare")))
                .title(DIVIDER, NamedTextColor.DARK_GREEN)
                .build();
        return golden(
                "bonus-tree-gift",
                "Bonus Tree Gift",
                "nether_star",
                "Bonus-drop announcement transcript.",
                features(Feature.BLOCK, Feature.CENTERED_BLOCK, Feature.BLOCK_SPACING, Feature.VALUE_HOVER),
                source(
                        "User screenshot transcript; cropped closing edge confirmed as the opening 64-character divider",
                        SCREENSHOT_SHA256
                ),
                new BlockOutput(block)
        );
    }

    private static List<Entry> createSynthesized() {
        return List.of(
                staffNotice(),
                rewardEarned(),
                accessDenied(),
                debugTrace(),
                collectionDiscovery(),
                objectiveProgress(),
                usefulLinks(),
                commandShortcuts(),
                readyCheck(),
                missionComplete(),
                seasonalEvent(),
                playerDirectoryEntry(),
                newArea()
        );
    }

    private static Entry staffNotice() {
        MessageBlock hover = Message.block()
                .title("RANK UPDATED", NamedTextColor.AQUA)
                .line("Applies across the network.")
                .build();
        InlineMessage message = Message.info(
                        "Granted {player} the {rank} rank.",
                        Message.slot("player", "Hqrxld"),
                        Message.slot("rank", ExampleMessageValues.rank("MVP+"))
                )
                .tag(Tag.STAFF)
                .hover(hover);
        return synthesized(
                "staff-notice",
                "Staff Notice",
                "name_tag",
                "Tagged network staff feedback.",
                features(Feature.NOTICE_INFO, Feature.TAG, Feature.MESSAGE_HOVER, Feature.SEMANTIC_UI_VALUES),
                new ChatOutput(message)
        );
    }

    private static Entry rewardEarned() {
        MessageBlock coinHover = Message.block()
                .title("COIN REWARD", NamedTextColor.GOLD)
                .line("Added directly to your purse.")
                .build();
        InlineMessage message = Message.success(
                "You earned {coins} and {xp}.",
                Message.slot("coins", Message.value(ExampleMessageValues.coinsReward(12_500)).hover(coinHover)),
                Message.slot("xp", ExampleMessageValues.xpReward(750, "Combat"))
        );
        return synthesized(
                "reward-earned",
                "Reward Earned",
                "emerald",
                "Success notice with rich values.",
                features(Feature.NOTICE_SUCCESS, Feature.VALUE_HOVER, Feature.SEMANTIC_UI_VALUES),
                new ChatOutput(message)
        );
    }

    private static Entry accessDenied() {
        InlineMessage message = Message.error(
                "You need {rank} to enter the Crystal Hall.",
                Message.slot("rank", ExampleMessageValues.rank("MVP+"))
        );
        return synthesized(
                "access-denied",
                "Access Denied",
                "barrier",
                "Concise gated-access feedback.",
                features(Feature.NOTICE_ERROR, Feature.SEMANTIC_UI_VALUES),
                new ChatOutput(message)
        );
    }

    private static Entry debugTrace() {
        InlineMessage message = Message.debug(
                "Waypoint {waypoint} resolved at {position}.",
                Message.slot("waypoint", "Crystal Hall"),
                Message.slot("position", "124, 72, -38")
        );
        return synthesized(
                "debug-trace",
                "Debug Trace",
                "comparator",
                "Restrained diagnostic output.",
                features(Feature.NOTICE_DEBUG),
                new ChatOutput(message)
        );
    }

    private static Entry collectionDiscovery() {
        InlineMessage message = Message.topic(
                        COLLECTION,
                        "You discovered {collection}!",
                        Message.slot("collection", gold("Ancient Relics"))
                )
                .hover(Message.block()
                        .title("ANCIENT RELICS", NamedTextColor.GOLD)
                        .line("Found throughout the Crystal Hollows.")
                        .build());
        return synthesized(
                "collection-discovery",
                "Collection Discovery",
                "experience_bottle",
                "Headline with supporting hover.",
                features(Feature.TOPIC, Feature.MESSAGE_HOVER),
                new ChatOutput(message)
        );
    }

    private static Entry objectiveProgress() {
        InlineMessage message = Message.success(
                "Objective: {current}/{target} Crystals restored",
                Message.slot("current", Message.value("3").color(NamedTextColor.AQUA)),
                Message.slot("target", Message.value("5").color(NamedTextColor.AQUA))
        );
        return synthesized(
                "objective-progress",
                "Objective Progress",
                "clock",
                "Compact action-bar progress.",
                features(Feature.NOTICE_SUCCESS, Feature.ACTION_BAR),
                new ActionBarOutput(message)
        );
    }

    private static Entry usefulLinks() {
        MessageValue wiki = Message.value(ExampleMessageValues.linkLabel("Wiki"))
                .click(Click.openUrl("https://example.com/wiki"));
        MessageValue discord = Message.value(Component.text("Discord", NamedTextColor.BLUE))
                .click(Click.openUrl("https://example.com/discord"));
        MessageBlock block = Message.block()
                .title("USEFUL LINKS", NamedTextColor.GREEN)
                .line(
                        "{click:wiki} for the {wiki}, or {click:discord} for {discord}.",
                        Message.slot("wiki", wiki),
                        Message.slot("discord", discord)
                )
                .build();
        return synthesized(
                "useful-links",
                "Useful Links",
                "iron_chain",
                "Two independent destinations.",
                features(Feature.BLOCK, Feature.CLICK_OPEN_URL, Feature.MULTI_CLICK),
                new BlockOutput(block)
        );
    }

    private static Entry commandShortcuts() {
        MessageValue warp = Message.value("Warp")
                .color(NamedTextColor.GREEN)
                .click(Click.runCommand("/warp hub"));
        MessageValue search = Message.value("Search")
                .color(NamedTextColor.AQUA)
                .click(Click.suggestCommand("/find "));
        MessageValue code = Message.value("Invite Code")
                .color(NamedTextColor.GOLD)
                .click(Click.copyToClipboard("CRYSTAL-7"));
        MessageBlock block = Message.block()
                .title("COMMAND SHORTCUTS", NamedTextColor.AQUA)
                .line(
                        "{click:warp} to {warp}, {click:search} to {search}, or {click:code} to copy the {code}.",
                        Message.slot("warp", warp),
                        Message.slot("search", search),
                        Message.slot("code", code)
                )
                .build();
        return synthesized(
                "command-shortcuts",
                "Command Shortcuts",
                "command_block",
                "Run, suggest, and copy actions.",
                features(
                        Feature.BLOCK,
                        Feature.CLICK_RUN_COMMAND,
                        Feature.CLICK_SUGGEST_COMMAND,
                        Feature.CLICK_COPY,
                        Feature.MULTI_CLICK
                ),
                new BlockOutput(block)
        );
    }

    private static Entry readyCheck() {
        MessageValue ready = Message.value(Component.text("[READY]", NamedTextColor.GREEN, TextDecoration.BOLD))
                .click(Click.runCommand("/ready yes"))
                .hover(Message.block().line("Confirm that you are ready.").build());
        MessageValue wait = Message.value(Component.text("[WAIT]", NamedTextColor.RED, TextDecoration.BOLD))
                .click(Click.runCommand("/ready no"))
                .hover(Message.block().line("Ask the party to wait.").build());
        MessageBlock block = Message.block()
                .title("DUNGEON READY CHECK", NamedTextColor.GOLD)
                .line(
                        "{click:ready} {ready}     {click:wait} {wait}",
                        Message.slot("ready", ready),
                        Message.slot("wait", wait)
                )
                .build();
        return synthesized(
                "ready-check",
                "Ready Check",
                "lever",
                "Two explicit party choices.",
                features(Feature.BLOCK, Feature.CLICK_RUN_COMMAND, Feature.MULTI_CLICK, Feature.VALUE_HOVER),
                new BlockOutput(block)
        );
    }

    private static Entry missionComplete() {
        MessageBlock block = Message.block()
                .title("MISSION COMPLETE", NamedTextColor.GREEN)
                .line(
                        "You restored the {site}.",
                        Message.slot("site", ExampleMessageValues.highlight("Sunken Observatory"))
                )
                .blank()
                .line("Rewards:")
                .bullet("{reward}", Message.slot("reward", Message.value(ExampleMessageValues.coinsReward(8_000))))
                .bullet("{reward}", Message.slot("reward", Message.value(ExampleMessageValues.xpReward(600, "Mining"))))
                .bullet("{reward}", Message.slot("reward", Message.value(ExampleMessageValues.unlock("Observatory Fast Travel"))))
                .build();
        return synthesized(
                "mission-complete",
                "Mission Complete",
                "written_book",
                "Structured reward block.",
                features(
                        Feature.BLOCK,
                        Feature.BLOCK_SPACING,
                        Feature.BULLETS,
                        Feature.SEMANTIC_UI_VALUES
                ),
                new BlockOutput(block)
        );
    }

    private static Entry seasonalEvent() {
        MessageBlock block = Message.centeredBlock()
                .title(DIVIDER, NamedTextColor.DARK_PURPLE)
                .title("STARFALL FESTIVAL", NamedTextColor.LIGHT_PURPLE)
                .line("Falling stars now contain {dust}.", Message.slot("dust", aqua("Astral Dust")))
                .line("The festival ends in {time}.", Message.slot("time", gold("30 minutes")))
                .title(DIVIDER, NamedTextColor.DARK_PURPLE)
                .build();
        return synthesized(
                "seasonal-event",
                "Seasonal Event",
                "firework_rocket",
                "Centered event announcement.",
                features(Feature.BLOCK, Feature.CENTERED_BLOCK),
                new BlockOutput(block)
        );
    }

    private static Entry playerDirectoryEntry() {
        return synthesized(
                "player-directory",
                "Player Directory",
                "player_head",
                "Paged chat directory.",
                features(Feature.BLOCK, Feature.CHAT_MENU_PAGING, Feature.CLICK_RUN_COMMAND),
                new BlockOutput(chatMenuPage(1))
        );
    }

    private static Entry newArea() {
        TitleMessage title = Message.title(
                Component.text("Celestial Ridge", NamedTextColor.AQUA),
                Component.text("NEW AREA DISCOVERED!", NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        MessageBlock block = Message.block()
                .title("AREA DISCOVERED", NamedTextColor.GOLD)
                .line(
                        "{area} is now available.",
                        Message.slot("area", Message.value("Celestial Ridge").color(NamedTextColor.AQUA))
                )
                .bullet("Find the Observatory Keeper")
                .bullet("Restore the western telescope")
                .build();
        return synthesized(
                "new-area",
                "New Area",
                "recovery_compass",
                "Title paired with local objectives.",
                features(Feature.TITLE_SUBTITLE, Feature.BLOCK, Feature.BULLETS),
                new TitleOutput(title),
                new BlockOutput(block)
        );
    }

    static MessageBlock chatMenuPage(int page) {
        int currentPage = Math.max(1, Math.min(page, 3));
        return Message.chatMenu("Online Adventurers")
                .page(currentPage)
                .pageSize(4)
                .previousCommand("/testmessages chatmenu " + Math.max(1, currentPage - 1))
                .nextCommand("/testmessages chatmenu " + (currentPage + 1))
                .row("{player} - {area}", Message.slot("player", green("Hqrxld")), Message.slot("area", "Hub"))
                .row("{player} - {area}", Message.slot("player", green("Swoop")), Message.slot("area", "Moonglade"))
                .row("{player} - {area}", Message.slot("player", green("Hina")), Message.slot("area", "Tangleburg"))
                .row("{player} - {area}", Message.slot("player", green("Starlyn")), Message.slot("area", "Contest Grounds"))
                .row("{player} - {area}", Message.slot("player", green("David")), Message.slot("area", "Hunter's Camp"))
                .row("{player} - {area}", Message.slot("player", green("Lupin")), Message.slot("area", "Rift"))
                .row("{player} - {area}", Message.slot("player", green("Ryan")), Message.slot("area", "Forest"))
                .row("{player} - {area}", Message.slot("player", green("Tim")), Message.slot("area", "Workshop"))
                .row("{player} - {area}", Message.slot("player", green("Sirius")), Message.slot("area", "Dark Auction"))
                .build();
    }

    private static MessageBlock coloredHover(String text, NamedTextColor color) {
        return Message.block()
                .line(
                        "{text}",
                        Message.slot("text", Message.value(text).color(color))
                )
                .build();
    }

    private static MessageValue areaTask(
            String prefix,
            String highlighted,
            NamedTextColor color,
            String suffix
    ) {
        return Message.value(Component.text()
                .append(Component.text(prefix, NamedTextColor.WHITE))
                .append(Component.text(highlighted, color))
                .append(Component.text(suffix, NamedTextColor.WHITE))
                .build());
    }

    private static MessageBlock questCompleteHover() {
        return Message.block()
                .line(
                        "{quest}",
                        Message.slot("quest", Message.value(Component.text()
                                .append(Component.text("Quest: ", NamedTextColor.YELLOW))
                                .append(Component.text("Swoop's Instructions", NamedTextColor.WHITE))
                                .build()))
                )
                .blank()
                .line("{step}", Message.slot("step", completedQuestStep("Talk to Swoop.")))
                .line("{step}", Message.slot("step", completedQuestStep("Collect the Berry.")))
                .line("{step}", Message.slot("step", completedQuestStep("Talk to Swoop.")))
                .line("{step}", Message.slot("step", completedQuestStep("Talk to Swoop.")))
                .line("{step}", Message.slot("step", completedQuestStep("Cut a Fig Tree.")))
                .line("{step}", Message.slot("step", completedQuestStep("Talk to Swoop.")))
                .blank()
                .line(
                        "{prompt}",
                        Message.slot("prompt", Message.value("Click to view your Quest Log!")
                                .color(NamedTextColor.YELLOW))
                )
                .build();
    }

    private static MessageValue completedQuestStep(String text) {
        return Message.value(Component.text()
                .append(Component.text("\u2714 ", NamedTextColor.GREEN))
                .append(Component.text(text, NamedTextColor.WHITE))
                .build());
    }

    private static MessageBlock coinsRewardHover() {
        return Message.block()
                .line(
                        "{line}",
                        Message.slot("line", Message.value(Component.text()
                                .append(Component.text("These ", NamedTextColor.GRAY))
                                .append(Component.text("Coins ", NamedTextColor.GOLD))
                                .append(Component.text("have been credited to", NamedTextColor.GRAY))
                                .build()))
                )
                .line(
                        "{line}",
                        Message.slot("line", Message.value(Component.text()
                                .append(Component.text("your ", NamedTextColor.GRAY))
                                .append(Component.text("Purse", NamedTextColor.GOLD))
                                .append(Component.text(".", NamedTextColor.GRAY))
                                .build()))
                )
                .blank()
                .line("To keep them safe and earn interest")
                .line(
                        "{line}",
                        Message.slot("line", Message.value(Component.text()
                                .append(Component.text("over time, deposit them at the ", NamedTextColor.GRAY))
                                .append(Component.text("Bank", NamedTextColor.GOLD))
                                .append(Component.text("!", NamedTextColor.GRAY))
                                .build()))
                )
                .build();
    }

    private static Component rewardComponent(String amount, NamedTextColor amountColor, String label) {
        return Component.text()
                .append(Component.text("+", NamedTextColor.DARK_GRAY))
                .append(Component.text(amount, amountColor))
                .append(Component.text(label, NamedTextColor.GRAY))
                .build();
    }

    private static MessageBlock rewardHover(String amount, NamedTextColor amountColor, String label) {
        return Message.block()
                .line(
                        "{reward}",
                        Message.slot("reward", Message.value(rewardComponent(amount, amountColor, label)))
                )
                .build();
    }

    private static MessageValue materialStashLine(Component line, MessageBlock hover) {
        return Message.value(line)
                .click(Click.runCommand("/viewstash material"))
                .hover(hover);
    }

    private static MessageBlock treeGiftRewardsHover() {
        return Message.block()
                .line("{reward}", Message.slot("reward", treeGiftReward("Forest Essence", NamedTextColor.DARK_GREEN, "x4")))
                .line("{reward}", Message.slot("reward", treeGiftReward("Foraging Experience", NamedTextColor.DARK_AQUA, "x2,000")))
                .line("{reward}", Message.slot("reward", treeGiftReward("HOTF Experience", NamedTextColor.GREEN, "x20")))
                .line("{reward}", Message.slot("reward", treeGiftReward("Tender Wood", NamedTextColor.GREEN, "x0-2")))
                .line("{reward}", Message.slot("reward", treeGiftReward("Forest Whispers", NamedTextColor.DARK_GREEN, "x40")))
                .build();
    }

    private static MessageValue treeGiftReward(String name, NamedTextColor color, String quantity) {
        return Message.value(Component.text()
                .append(Component.text(name, color))
                .append(Component.space())
                .append(Component.text(quantity, NamedTextColor.DARK_GRAY))
                .build());
    }

    private static MessageValue treeGiftRewardLine(MessageBlock hover) {
        return Message.value(Component.text()
                        .append(Component.text("+5 rewards gained!", NamedTextColor.YELLOW))
                        .append(Component.space())
                        .append(Component.text("(hover)", NamedTextColor.DARK_GRAY))
                        .build())
                .hover(hover);
    }

    private static Component unlockComponent(String quest) {
        return Component.text()
                .append(Component.text("NEW QUEST UNLOCKED", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(": ", NamedTextColor.WHITE))
                .append(Component.text(quest, NamedTextColor.WHITE))
                .build();
    }

    private static Entry golden(
            String id,
            String label,
            String icon,
            String description,
            Set<Feature> features,
            Source source,
            Output... outputs
    ) {
        return new Entry(
                id,
                label,
                icon,
                description,
                Origin.CORPUS_GOLDEN,
                features,
                Optional.of(source),
                List.of(outputs)
        );
    }

    private static Entry synthesized(
            String id,
            String label,
            String icon,
            String description,
            Set<Feature> features,
            Output... outputs
    ) {
        return new Entry(
                id,
                label,
                icon,
                description,
                Origin.SYNTHESIZED,
                features,
                Optional.empty(),
                List.of(outputs)
        );
    }

    private static Source source(String description, String screenshotSha256, String... messageSha256) {
        return new Source(description, List.of(messageSha256), Optional.ofNullable(screenshotSha256));
    }

    private static Set<Feature> features(Feature first, Feature... rest) {
        EnumSet<Feature> features = EnumSet.of(first, rest);
        return Set.copyOf(features);
    }

    private static MessageValue white(String text) {
        return Message.value(text).color(NamedTextColor.WHITE);
    }

    private static MessageValue greenBold(String text) {
        return Message.value(Component.text(text, NamedTextColor.GREEN, TextDecoration.BOLD));
    }

    private static MessageValue aqua(String text) {
        return Message.value(text).color(NamedTextColor.AQUA);
    }

    private static MessageValue green(String text) {
        return Message.value(text).color(NamedTextColor.GREEN);
    }

    private static MessageValue gold(String text) {
        return Message.value(text).color(NamedTextColor.GOLD);
    }

    private static MessageValue red(String text) {
        return Message.value(text).color(NamedTextColor.RED);
    }

    private static MessageValue purple(String text) {
        return Message.value(text).color(NamedTextColor.LIGHT_PURPLE);
    }

    enum Origin {
        CORPUS_GOLDEN,
        SYNTHESIZED
    }

    enum Feature {
        NOTICE_INFO,
        NOTICE_SUCCESS,
        NOTICE_ERROR,
        NOTICE_DEBUG,
        TAG,
        TOPIC,
        BLOCK,
        BLOCK_SPACING,
        BULLETS,
        CENTERED_BLOCK,
        TITLE_SUBTITLE,
        ACTION_BAR,
        CLICK_OPEN_URL,
        CLICK_RUN_COMMAND,
        CLICK_SUGGEST_COMMAND,
        CLICK_COPY,
        MULTI_CLICK,
        VALUE_HOVER,
        MESSAGE_HOVER,
        CHAT_MENU_PAGING,
        SEMANTIC_UI_VALUES
    }

    record Source(String description, List<String> messageSha256, Optional<String> screenshotSha256) {

        Source {
            Objects.requireNonNull(description, "description");
            messageSha256 = List.copyOf(Objects.requireNonNull(messageSha256, "messageSha256"));
            screenshotSha256 = Objects.requireNonNull(screenshotSha256, "screenshotSha256");
            messageSha256.forEach(Source::requireSha256);
            screenshotSha256.ifPresent(Source::requireSha256);
            if (messageSha256.isEmpty() && screenshotSha256.isEmpty()) {
                throw new IllegalArgumentException("source must reference corpus messages or a screenshot");
            }
        }

        private static void requireSha256(String value) {
            if (value == null || !value.matches("[0-9A-Fa-f]{64}")) {
                throw new IllegalArgumentException("source hash must be a SHA-256 value");
            }
        }
    }

    record Entry(
            String id,
            String label,
            String icon,
            String description,
            Origin origin,
            Set<Feature> features,
            Optional<Source> source,
            List<Output> outputs
    ) {

        Entry {
            if (Objects.requireNonNull(id, "id").isBlank()) {
                throw new IllegalArgumentException("id cannot be blank");
            }
            if (Objects.requireNonNull(label, "label").isBlank()) {
                throw new IllegalArgumentException("label cannot be blank");
            }
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(origin, "origin");
            features = Set.copyOf(Objects.requireNonNull(features, "features"));
            source = Objects.requireNonNull(source, "source");
            outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
            if (outputs.isEmpty()) {
                throw new IllegalArgumentException("outputs cannot be empty");
            }
            if ((origin == Origin.CORPUS_GOLDEN) != source.isPresent()) {
                throw new IllegalArgumentException("only corpus goldens may carry source metadata");
            }
        }
    }

    sealed interface Output permits ChatOutput, BlockOutput, ActionBarOutput, TitleOutput {

        void send(MinestomMessageSender sender, Player player);
    }

    record ChatOutput(InlineMessage message) implements Output {

        ChatOutput {
            Objects.requireNonNull(message, "message");
        }

        @Override
        public void send(MinestomMessageSender sender, Player player) {
            sender.send(player, message);
        }
    }

    record BlockOutput(MessageBlock block) implements Output {

        BlockOutput {
            Objects.requireNonNull(block, "block");
        }

        @Override
        public void send(MinestomMessageSender sender, Player player) {
            sender.send(player, block);
        }
    }

    record ActionBarOutput(InlineMessage message) implements Output {

        ActionBarOutput {
            Objects.requireNonNull(message, "message");
        }

        @Override
        public void send(MinestomMessageSender sender, Player player) {
            sender.sendActionBar(player, message);
        }
    }

    record TitleOutput(TitleMessage message) implements Output {

        TitleOutput {
            Objects.requireNonNull(message, "message");
        }

        @Override
        public void send(MinestomMessageSender sender, Player player) {
            sender.showTitle(player, message);
        }
    }
}
