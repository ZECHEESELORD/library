package sh.harold.creative.library.example.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import org.bukkit.Material;
import sh.harold.creative.library.menu.AccentFamily;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuDefinition;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.MenuItem;
import sh.harold.creative.library.menu.MenuPair;
import sh.harold.creative.library.menu.MenuStack;
import sh.harold.creative.library.menu.MenuTab;
import sh.harold.creative.library.menu.MenuTabContent;
import sh.harold.creative.library.menu.MenuTabGroup;
import sh.harold.creative.library.menu.ReactiveGeometryAction;
import sh.harold.creative.library.menu.ReactiveMenuEffect;
import sh.harold.creative.library.menu.ReactiveMenu;
import sh.harold.creative.library.menu.ReactiveMenuInput;
import sh.harold.creative.library.menu.ReactiveMenuResult;
import sh.harold.creative.library.menu.ReactiveListView;
import sh.harold.creative.library.menu.ReactiveTabsView;
import sh.harold.creative.library.menu.ReactiveMenuView;
import sh.harold.creative.library.menu.UtilitySlot;
import sh.harold.creative.library.menu.paper.PaperMenuPlatform;
import sh.harold.creative.library.sound.SoundCueKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

final class PaperMenuExampleMenus {

    private static final String TOGGLE_LOCK = "toggle-lock";
    private static final int TAB_CANVAS_LEFT_SLOT = 29;
    private static final int TAB_CANVAS_CENTER_SLOT = 31;
    private static final int TAB_CANVAS_RIGHT_SLOT = 33;
    private static final int TRUE_CANVAS_LEFT_SLOT = 20;
    private static final int TRUE_CANVAS_CENTER_SLOT = 22;
    private static final int TRUE_CANVAS_RIGHT_SLOT = 24;
    private static final int SNAKE_BOARD_COLUMNS = 9;
    private static final int SNAKE_BOARD_ROWS = 5;
    private static final int SNAKE_BOARD_LIMIT = SNAKE_BOARD_COLUMNS * SNAKE_BOARD_ROWS;
    private static final int NO_PREVIOUS_SNAKE_SLOT = -1;
    private static final int NO_SOURCE_SLOT = -1;

    private final PaperMenuPlatform menus;
    private final Menu profilePreviewMenu;
    private final Menu farmingPreviewMenu;
    private final Menu museumPreviewMenu;
    private final Menu slotFivePreviewMenu;
    private final MenuDisplayItem snakeInfoDisplay;
    private final MenuDisplayItem snakeHeadDisplay;
    private final MenuDisplayItem dragLockInfoDisplay;
    private final MenuDisplayItem dragLockEmptyDisplay;
    private final MenuDisplayItem clickLockInfoDisplay;
    private final MenuDisplayItem clickLockEmptyDisplay;
    private final MenuButton lockToggleUtilityButton;
    private final ReactiveMenu reactiveListDemoMenu;
    private final ReactiveMenu reactiveTabsDemoMenu;
    private final ReactiveMenu snakeDemoMenu;
    private final ReactiveMenu lockDragDemoMenu;
    private final ReactiveMenu lockClickDemoMenu;
    private final Menu listGalleryMenu;
    private final Menu canvasGalleryMenu;
    private final Menu reactiveGalleryMenu;
    private final Menu tabsGalleryMenu;

    PaperMenuExampleMenus(PaperMenuPlatform menus) {
        this.menus = menus;
        this.profilePreviewMenu = preview("Your SkyBlock Profile Preview", yourSkyBlockProfileDisplay());
        this.farmingPreviewMenu = preview("Farming XLIX Preview", farmingXlixDisplay());
        this.museumPreviewMenu = preview("Museum Rewards Preview", museumRewardsDisplay());
        this.slotFivePreviewMenu = preview("Profile Slot #5 Preview", profileSlotFiveDisplay());
        this.snakeInfoDisplay = infoDisplay(Material.COMPASS, FakeSkyBlockMenuTitles.special("Reactive Snake"),
                "This one-slot snake picks a new orthogonal step on every tick so the runtime can patch only the changed slots.",
                "Only the old and new head slots should repaint.",
                "The menu stays open while the session state ticks.");
        this.snakeHeadDisplay = menus.display(Material.SLIME_BALL)
                .name(FakeSkyBlockMenuTitles.special("Snake Head"))
                .description("A one-slot live actor moving through the compiled chrome.")
                .build();
        this.dragLockInfoDisplay = infoDisplay(Material.HOPPER, FakeSkyBlockMenuTitles.perk("Shift Or Drag"),
                "Click a stack in your inventory to pick it up without duplicating it, then click or drag across the open center slot to insert it.",
                "Shift-click inserts straight into the center slot when it is unlocked.",
                "Plain-click the loaded stack to pick it back up.",
                "Shift-click the loaded stack to return it to its original inventory slot.");
        this.dragLockEmptyDisplay = menus.display(Material.AIR)
                .name(Component.text(" "))
                .build();
        this.clickLockInfoDisplay = infoDisplay(Material.CHEST, FakeSkyBlockMenuTitles.normal("Inventory Click Insert"),
                "Click any stack in the bottom inventory to move it into the center slot with no duplication.",
                "Click the loaded stack to return it to the original inventory slot.",
                "No cursor carrying is involved in this variant.",
                "The lock still blocks inserts and returns.");
        this.clickLockEmptyDisplay = menus.display(Material.STONE_BUTTON)
                .name(FakeSkyBlockMenuTitles.normal("Click To Load"))
                .description("Click an inventory stack to move it here, then click the loaded stack to send it back.")
                .bullets(
                        "Moves the clicked stack out of your inventory immediately.",
                        "Returns the stored stack to its original slot when clicked again.")
                .build();
        this.lockToggleUtilityButton = lockToggleButton();
        this.reactiveListDemoMenu = buildReactiveListDemo();
        this.reactiveTabsDemoMenu = buildReactiveTabsDemo();
        this.snakeDemoMenu = buildSnakeDemo();
        this.lockDragDemoMenu = buildLockDragDemo();
        this.lockClickDemoMenu = buildLockClickDemo();
        this.listGalleryMenu = buildListGallery();
        this.canvasGalleryMenu = buildCanvasGallery();
        this.reactiveGalleryMenu = buildReactiveGallery();
        this.tabsGalleryMenu = buildTabsGallery();
    }

    Menu gallery() {
        return tabsGalleryMenu;
    }

    Menu tabsGallery() {
        return tabsGalleryMenu;
    }

    Menu listGallery() {
        return listGalleryMenu;
    }

    Menu canvasGallery() {
        return canvasGalleryMenu;
    }

    Menu reactiveGallery() {
        return reactiveGalleryMenu;
    }

    Menu profilePreview() {
        return profilePreviewMenu;
    }

    Menu farmingPreview() {
        return farmingPreviewMenu;
    }

    Menu museumPreview() {
        return museumPreviewMenu;
    }

    Menu slotFivePreview() {
        return slotFivePreviewMenu;
    }

    MenuDefinition snakeDemo() {
        return snakeDemoMenu;
    }

    MenuDefinition lockDragDemo() {
        return lockDragDemoMenu;
    }

    MenuDefinition lockClickDemo() {
        return lockClickDemoMenu;
    }

    private Menu buildTabsGallery() {
        return menus.tabs()
                .title("House Style Gallery")
                .defaultTab("profiles")
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
                                        builder -> builder.place(TAB_CANVAS_CENTER_SLOT, infoDisplay(Material.COMPASS, FakeSkyBlockMenuTitles.perk("Centered Canvas"),
                                                "Canvas tabs should bias primary content to row 3, centered first.",
                                                "One item: (4,3)",
                                                "Two items: (2,3) and (6,3)",
                                                "Expand outward from there.")),
                                        "Centered row-3 placements first",
                                        "Leave list tabs partially open",
                                        "Use filler only when it helps"),
                                canvasTab("showcase", FakeSkyBlockMenuTitles.special("Featured Cards"), Material.ITEM_FRAME,
                                        "Preview a couple of fully authored cards side by side so title tone, spacing, and grouped blocks read together.",
                                        builder -> {
                                            builder.place(TAB_CANVAS_LEFT_SLOT, yourSkyBlockProfileDisplay());
                                            builder.place(TAB_CANVAS_RIGHT_SLOT, farmingXlixDisplay());
                                        },
                                        "Colored titles by feel",
                                        "Grouped stat and progress blocks",
                                        "Prompt-last house copy"),
                                canvasTab("routes", FakeSkyBlockMenuTitles.normal("Demo Routes"), Material.BOOKSHELF,
                                        "Jump between the compiled list gallery, the reactive geometry gallery, and the true fixed-slot canvas gallery from one route tab.",
                                        builder -> {
                                            builder.place(TAB_CANVAS_LEFT_SLOT, openMenuButton(Material.CHEST, FakeSkyBlockMenuTitles.normal("Open List Gallery"),
                                                    "Jump into the plain paged list example.", ActionVerb.OPEN, listGalleryMenu));
                                            builder.place(TAB_CANVAS_CENTER_SLOT, openMenuButton(Material.SLIME_BALL, FakeSkyBlockMenuTitles.special("Open Reactive Gallery"),
                                                    "Open the routed reactive demos for list, tabs, canvas, drag, and inventory-click testing.", ActionVerb.OPEN, reactiveGalleryMenu));
                                            builder.place(TAB_CANVAS_RIGHT_SLOT, openMenuButton(Material.ITEM_FRAME, FakeSkyBlockMenuTitles.normal("Open True Canvas Gallery"),
                                                    "Jump into the true fixed-slot canvas example centered on row 2.", ActionVerb.OPEN, canvasGalleryMenu));
                                        },
                                        "Open paged list gallery",
                                        "Open reactive geometry gallery",
                                        "Open true fixed-slot canvas gallery")
                        )))
                .build();
    }

    private Menu buildListGallery() {
        return menus.list()
                .title("House Style List Gallery")
                .addItems(listGalleryButtons())
                .build();
    }

    private Menu buildCanvasGallery() {
        return menus.canvas()
                .title("True Canvas Gallery")
                .place(TRUE_CANVAS_LEFT_SLOT, yourSkyBlockProfileDisplay())
                .place(TRUE_CANVAS_CENTER_SLOT, farmingXlixDisplay())
                .place(TRUE_CANVAS_RIGHT_SLOT, museumRewardsDisplay())
                .build();
    }

    private Menu buildReactiveGallery() {
        return menus.list()
                .title("Reactive Menu Gallery")
                .addItems(List.of(
                        reactiveLaunchButton(Material.WRITABLE_BOOK, FakeSkyBlockMenuTitles.normal("Reactive List Browser"),
                                "Browse a live pure list where the highlighted entry changes its lore as you page and select entries.",
                                ActionVerb.OPEN,
                                reactiveListDemoMenu,
                                MenuPair.of("Mode", FakeSkyBlockMenuValues.tracked("Paged lore updates")),
                                MenuPair.of("Shape", FakeSkyBlockMenuValues.ready("Pure list geometry"))),
                        reactiveLaunchButton(Material.CLOCK, FakeSkyBlockMenuTitles.normal("Reactive Tabs Switchboard"),
                                "Switch grouped tabs, scroll the tab strip, and watch the active tab content re-render without rebuilding the menu.",
                                ActionVerb.OPEN,
                                reactiveTabsDemoMenu,
                                MenuPair.of("Mode", FakeSkyBlockMenuValues.tracked("Strip plus page state")),
                                MenuPair.of("Shape", FakeSkyBlockMenuValues.ready("Grouped tab geometry"))),
                        reactiveLaunchButton(Material.SLIME_BALL, FakeSkyBlockMenuTitles.special("Reactive Snake"),
                                "Watch a one-slot snake wander the menu grid one orthogonal step at a time while the runtime patches only the slots that changed.",
                                ActionVerb.OPEN,
                                snakeDemoMenu,
                                MenuPair.of("Mode", FakeSkyBlockMenuValues.tracked("Ticked diff updates")),
                                MenuPair.of("Shape", FakeSkyBlockMenuValues.ready("One-slot orthogonal walk"))),
                        reactiveLaunchButton(Material.HOPPER, FakeSkyBlockMenuTitles.perk("Lockable Shift & Drag Slot"),
                                "Shift-click or click a bottom-inventory stack to claim it by source slot, then drag or click it into the center slot.",
                                ActionVerb.OPEN,
                                lockDragDemoMenu,
                                MenuPair.of("Mode", FakeSkyBlockMenuValues.tracked("Source-slot ownership")),
                                MenuPair.of("Focus", FakeSkyBlockMenuValues.ready("Open air target"))),
                        reactiveLaunchButton(Material.CHEST, FakeSkyBlockMenuTitles.normal("Lockable Inventory Click Slot"),
                                "Click an inventory stack to load it into the center slot, then click the loaded slot to return it to the same source slot.",
                                ActionVerb.OPEN,
                                lockClickDemoMenu,
                                MenuPair.of("Mode", FakeSkyBlockMenuValues.tracked("Click insert / click return")),
                                MenuPair.of("Focus", FakeSkyBlockMenuValues.ready("No duplication")))))
                .build();
    }

    private ReactiveMenu buildReactiveListDemo() {
        return menus.reactiveList()
                .state(new ReactiveListState(0, 3))
                .render(state -> ReactiveListView.builder("Reactive List Browser")
                        .page(state.pageIndex())
                        .addItems(reactiveListItems(state.highlightedIndex()))
                        .build())
                .reduce((state, input) -> reduceReactiveListState(state, input))
                .build();
    }

    private ReactiveMenu buildReactiveTabsDemo() {
        return menus.reactiveTabs()
                .state(new ReactiveTabsState("overview", 0, 0, 1))
                .render(state -> ReactiveTabsView.builder("Reactive Tabs Switchboard")
                        .activeTab(state.activeTabId())
                        .navStart(state.navStart())
                        .page(state.pageIndex())
                        .addGroup(MenuTabGroup.of("browse", List.of(
                                menus.tab("overview", Material.BOOK)
                                        .name(FakeSkyBlockMenuTitles.normal("Reactive Overview"))
                                        .description("A live list tab whose item lore changes when the focused entry moves.")
                                        .pairs(
                                                MenuPair.of("State", FakeSkyBlockMenuValues.tracked("Paged entry focus")),
                                                MenuPair.of("View", FakeSkyBlockMenuValues.ready("Content updates in place")))
                                        .items(reactiveTabsOverviewItems(state))
                                        .build(),
                                menus.tab("signals", Material.COMPARATOR)
                                        .name(FakeSkyBlockMenuTitles.perk("Reactive Signals"))
                                        .description("A second list tab that keeps the tab strip honest and shows shared footer paging.")
                                        .pairs(
                                                MenuPair.of("State", FakeSkyBlockMenuValues.tracked("Selection echo")),
                                                MenuPair.of("View", FakeSkyBlockMenuValues.ready("Footer-owned paging")))
                                        .items(reactiveTabsSignalItems(state))
                                        .build())))
                        .addGroup(MenuTabGroup.of("workspace", List.of(
                                menus.tab("canvas", Material.MAP)
                                        .name(FakeSkyBlockMenuTitles.special("Reactive Canvas Board"))
                                        .description("A live canvas tab with centered slots and a small note that shifts with state.")
                                        .pairs(
                                                MenuPair.of("State", FakeSkyBlockMenuValues.tracked("Canvas chrome")),
                                                MenuPair.of("View", FakeSkyBlockMenuValues.ready("Explicit slot placement")))
                                        .canvas(builder -> reactiveTabsCanvas(builder, state))
                                        .build(),
                                menus.tab("notes", Material.WRITABLE_BOOK)
                                        .name(FakeSkyBlockMenuTitles.success("Reactive Notes"))
                                        .description("A compact canvas-style tab that proves custom footer and tab strip ownership stay separate.")
                                        .pairs(
                                                MenuPair.of("State", FakeSkyBlockMenuValues.tracked("Canvas footer")),
                                                MenuPair.of("View", FakeSkyBlockMenuValues.ready("Tab chrome stays owned")))
                                        .canvas(builder -> reactiveTabsNotes(builder, state))
                                        .build())))
                        .build())
                .reduce((state, input) -> reduceReactiveTabsState(state, input))
                .build();
    }

    private ReactiveMenu buildSnakeDemo() {
        return menus.reactiveCanvas()
                .state(new SnakeState(0, NO_PREVIOUS_SNAKE_SLOT, 0x51A7E5L))
                .tickEvery(4L)
                .render(state -> ReactiveMenuView.builder("Reactive Snake")
                        .place(TRUE_CANVAS_CENTER_SLOT, snakeInfoDisplay)
                        .place(state.slot(), snakeHeadDisplay)
                        .build())
                .reduce((state, input) -> input instanceof ReactiveMenuInput.Tick
                        ? ReactiveMenuResult.stay(nextSnakeState(state))
                        : ReactiveMenuResult.stay(state))
                .build();
    }

    private ReactiveMenu buildLockDragDemo() {
        return menus.reactiveCanvas()
                .utility(UtilitySlot.LEFT_1, lockToggleUtilityButton)
                .state(new DragLockState(null, NO_SOURCE_SLOT, null, NO_SOURCE_SLOT, false,
                        "Click an inventory stack to pick it up, or shift-click one to insert it directly."))
                .render(state -> ReactiveMenuView.builder("Reactive Lock Demo")
                        .cursor(state.cursor())
                        .place(TRUE_CANVAS_LEFT_SLOT, dragLockInfoDisplay)
                        .place(TRUE_CANVAS_CENTER_SLOT, dragLockTarget(state))
                        .place(TRUE_CANVAS_RIGHT_SLOT, dragLockStatus(state))
                        .build())
                .reduce((state, input) -> reduceDragLockState(state, input))
                .build();
    }

    private ReactiveMenu buildLockClickDemo() {
        return menus.reactiveCanvas()
                .utility(UtilitySlot.LEFT_1, lockToggleUtilityButton)
                .state(new ClickLockState(null, NO_SOURCE_SLOT, false,
                        "Click an inventory stack to move it into the center slot."))
                .render(state -> ReactiveMenuView.builder("Reactive Click Demo")
                        .place(TRUE_CANVAS_LEFT_SLOT, clickLockInfoDisplay)
                        .place(TRUE_CANVAS_CENTER_SLOT, clickLockTarget(state))
                        .place(TRUE_CANVAS_RIGHT_SLOT, clickLockStatus(state))
                        .build())
                .reduce((state, input) -> reduceClickLockState(state, input))
                .build();
    }

    private ReactiveMenuResult<ReactiveListState> reduceReactiveListState(ReactiveListState state, ReactiveMenuInput input) {
        if (input instanceof ReactiveMenuInput.Click click) {
            if (click.message() instanceof ReactiveGeometryAction.PreviousPage) {
                return ReactiveMenuResult.stay(new ReactiveListState(Math.max(0, state.pageIndex() - 1), state.highlightedIndex()));
            }
            if (click.message() instanceof ReactiveGeometryAction.NextPage) {
                return ReactiveMenuResult.stay(new ReactiveListState(state.pageIndex() + 1, state.highlightedIndex()));
            }
            if (click.message() instanceof String message && message.startsWith("focus:")) {
                int index = Integer.parseInt(message.substring("focus:".length()));
                return ReactiveMenuResult.stay(new ReactiveListState(state.pageIndex(), index));
            }
        }
        return ReactiveMenuResult.stay(state);
    }

    private ReactiveMenuResult<ReactiveTabsState> reduceReactiveTabsState(ReactiveTabsState state, ReactiveMenuInput input) {
        if (input instanceof ReactiveMenuInput.Click click) {
            Object message = click.message();
            if (message instanceof ReactiveGeometryAction.SwitchTab switchTab) {
                return ReactiveMenuResult.stay(new ReactiveTabsState(switchTab.tabId(), state.navStart(), state.pageIndex(), state.focusedIndex()));
            }
            if (message instanceof ReactiveGeometryAction.PreviousTabs) {
                return ReactiveMenuResult.stay(new ReactiveTabsState(state.activeTabId(), Math.max(0, state.navStart() - 1), state.pageIndex(), state.focusedIndex()));
            }
            if (message instanceof ReactiveGeometryAction.NextTabs) {
                return ReactiveMenuResult.stay(new ReactiveTabsState(state.activeTabId(), state.navStart() + 1, state.pageIndex(), state.focusedIndex()));
            }
            if (message instanceof ReactiveGeometryAction.JumpToFirstTabs) {
                return ReactiveMenuResult.stay(new ReactiveTabsState(state.activeTabId(), 0, state.pageIndex(), state.focusedIndex()));
            }
            if (message instanceof ReactiveGeometryAction.JumpToLastTabs) {
                return ReactiveMenuResult.stay(new ReactiveTabsState(state.activeTabId(), Integer.MAX_VALUE, state.pageIndex(), state.focusedIndex()));
            }
            if (message instanceof ReactiveGeometryAction.PreviousPage) {
                return ReactiveMenuResult.stay(new ReactiveTabsState(state.activeTabId(), state.navStart(), Math.max(0, state.pageIndex() - 1), state.focusedIndex()));
            }
            if (message instanceof ReactiveGeometryAction.NextPage) {
                return ReactiveMenuResult.stay(new ReactiveTabsState(state.activeTabId(), state.navStart(), state.pageIndex() + 1, state.focusedIndex()));
            }
            if (message instanceof String focus && focus.startsWith("focus:")) {
                int index = Integer.parseInt(focus.substring("focus:".length()));
                return ReactiveMenuResult.stay(new ReactiveTabsState(state.activeTabId(), state.navStart(), state.pageIndex(), index));
            }
        }
        return ReactiveMenuResult.stay(state);
    }

    private List<MenuItem> reactiveListItems(int highlightedIndex) {
        return IntStream.range(0, 35)
                .mapToObj(index -> reactiveListEntry(index, highlightedIndex))
                .toList();
    }

    private MenuItem reactiveListEntry(int index, int highlightedIndex) {
        boolean highlighted = index == highlightedIndex;
        return menus.button(Material.PAPER)
                .name(FakeSkyBlockMenuTitles.normal("List Entry " + index))
                .secondary(highlighted ? "Selected" : "Reactive row")
                .description(highlighted
                        ? "This entry is currently focused, so its lore reflects the active selection."
                        : "Click to move the focus here and update the rendered lore.")
                .pairs(
                        MenuPair.of("Index", FakeSkyBlockMenuValues.tracked(String.valueOf(index))),
                        MenuPair.of("State", FakeSkyBlockMenuValues.ready(highlighted ? "Focused" : "Idle")))
                .emit(ActionVerb.VIEW, "focus:" + index)
                .build();
    }

    private List<MenuItem> reactiveTabsOverviewItems(ReactiveTabsState state) {
        return IntStream.range(0, 29)
                .mapToObj(index -> menus.button(Material.BOOK)
                        .name(FakeSkyBlockMenuTitles.normal("Overview Entry " + index))
                        .secondary(index == state.focusedIndex() ? "Selected" : "Browse list")
                        .description(index == state.focusedIndex()
                                ? "This entry is the current focus for the reactive list tab."
                                : "Click to move the tab's highlighted lore row here.")
                        .emit(ActionVerb.VIEW, "focus:" + index)
                        .build())
                .toList();
    }

    private List<MenuItem> reactiveTabsSignalItems(ReactiveTabsState state) {
        return IntStream.range(0, 18)
                .mapToObj(index -> menus.button(Material.COMPASS)
                        .name(FakeSkyBlockMenuTitles.perk("Signal " + index))
                        .secondary(index == state.focusedIndex() ? "Selected" : "Reactive signal")
                        .description(index == state.focusedIndex()
                                ? "This signal row is currently highlighted in the reactive tabs demo."
                                : "Click to retarget the highlight to this signal row.")
                        .emit(ActionVerb.VIEW, "focus:" + index)
                        .build())
                .toList();
    }

    private void reactiveTabsCanvas(MenuTabContent.CanvasBuilder builder, ReactiveTabsState state) {
        builder.place(TRUE_CANVAS_LEFT_SLOT, menus.display(Material.CHEST)
                        .name(FakeSkyBlockMenuTitles.normal("Canvas Anchor"))
                        .description("The canvas tab keeps its own slot math and still updates as state changes.")
                        .build())
                .place(TRUE_CANVAS_CENTER_SLOT, menus.display(Material.MAP)
                        .name(FakeSkyBlockMenuTitles.special("Focused " + state.focusedIndex()))
                        .description("This centered card reflects the reactive tab state.")
                        .build())
                .place(TRUE_CANVAS_RIGHT_SLOT, menus.display(Material.WRITABLE_BOOK)
                        .name(FakeSkyBlockMenuTitles.success("Owned Footer"))
                        .description("Tab chrome and footer ownership remain separate concerns.")
                        .build());
    }

    private void reactiveTabsNotes(MenuTabContent.CanvasBuilder builder, ReactiveTabsState state) {
        builder.fillWithBlackPane(false)
                .place(29, menus.display(Material.PAPER)
                        .name(FakeSkyBlockMenuTitles.normal("Strip Note"))
                        .description("Canvas tabs can stay open where they need negative space.")
                        .build())
                .place(31, menus.display(Material.BOOK)
                        .name(FakeSkyBlockMenuTitles.perk("Active " + state.activeTabId()))
                        .description("The active tab id and strip position both live in reducer state.")
                        .build())
                .place(33, menus.display(Material.COMPARATOR)
                        .name(FakeSkyBlockMenuTitles.reward("Nav Start " + state.navStart()))
                        .description("Scroll state stays explicit instead of being inferred from titles.")
                        .build());
    }

    private ReactiveMenuResult<DragLockState> reduceDragLockState(DragLockState state, ReactiveMenuInput input) {
        if (input instanceof ReactiveMenuInput.Click click && TOGGLE_LOCK.equals(click.message())) {
            boolean locked = !state.locked();
            return ReactiveMenuResult.stay(new DragLockState(state.stored(), state.storedSourceSlot(), state.cursor(),
                    state.cursorSourceSlot(), locked, locked ? "Locked the center slot." : "Unlocked the center slot."));
        }
        if (input instanceof ReactiveMenuInput.InventoryClick click) {
            if (state.cursor() != null) {
                if (click.item() == null) {
                    return placeCursorIntoViewerSlot(state, click.slot(), "Placed the carried stack into that inventory slot.");
                }
                return ReactiveMenuResult.stay(new DragLockState(state.stored(), state.storedSourceSlot(), state.cursor(),
                        state.cursorSourceSlot(), state.locked(), "Pick an empty inventory slot before placing the carried stack."));
            }
            if (click.item() == null) {
                return ReactiveMenuResult.stay(state);
            }
            if (click.shift()) {
                if (state.locked()) {
                    return ReactiveMenuResult.stay(new DragLockState(state.stored(), state.storedSourceSlot(), state.cursor(),
                            state.cursorSourceSlot(), true, "Unlock the center slot before shift-inserting a stack."));
                }
                if (state.stored() != null) {
                    return ReactiveMenuResult.stay(new DragLockState(state.stored(), state.storedSourceSlot(), state.cursor(),
                            state.cursorSourceSlot(), false, "The center slot already holds a stack."));
                }
                return ReactiveMenuResult.of(
                        new DragLockState(click.item(), click.slot(), state.cursor(), state.cursorSourceSlot(), false,
                                "Shift-clicked a stack into the center slot."),
                        new ReactiveMenuEffect.SetViewerInventorySlot(click.slot(), null));
            }
            return ReactiveMenuResult.of(
                    new DragLockState(state.stored(), state.storedSourceSlot(), click.item(), click.slot(), false,
                            "Picked up the clicked stack from your inventory."),
                    new ReactiveMenuEffect.SetViewerInventorySlot(click.slot(), null));
        }
        if (input instanceof ReactiveMenuInput.Drag drag
                && drag.cursor() != null
                && drag.slots().contains(TRUE_CANVAS_CENTER_SLOT)) {
            if (state.locked()) {
                return ReactiveMenuResult.stay(new DragLockState(state.stored(), state.storedSourceSlot(), state.cursor(),
                        state.cursorSourceSlot(), true, "Unlock the center slot before changing its contents."));
            }
            if (state.cursor() == null) {
                return ReactiveMenuResult.stay(new DragLockState(state.stored(), state.storedSourceSlot(), state.cursor(),
                        state.cursorSourceSlot(), false, "Load a stack before dragging it into the center slot."));
            }
            if (state.stored() != null) {
                return ReactiveMenuResult.stay(new DragLockState(state.stored(), state.storedSourceSlot(), state.cursor(),
                        state.cursorSourceSlot(), false, "The center slot already holds a stack."));
            }
            return ReactiveMenuResult.stay(new DragLockState(state.cursor(), state.cursorSourceSlot(), null, NO_SOURCE_SLOT,
                    false, "Dragged the loaded stack into the center slot."));
        }
        if (input instanceof ReactiveMenuInput.Click click && click.slot() == TRUE_CANVAS_CENTER_SLOT) {
            if (state.locked()) {
                return ReactiveMenuResult.stay(new DragLockState(state.stored(), state.storedSourceSlot(), state.cursor(),
                        state.cursorSourceSlot(), true, "Unlock the center slot before moving stacks."));
            }
            if (state.cursor() != null) {
                return ReactiveMenuResult.stay(new DragLockState(state.cursor(), state.cursorSourceSlot(), null, NO_SOURCE_SLOT,
                        false, "Placed the loaded stack into the center slot."));
            }
            if (state.stored() == null) {
                return ReactiveMenuResult.stay(new DragLockState(null, NO_SOURCE_SLOT, null, NO_SOURCE_SLOT, false,
                        "The center slot is empty."));
            }
            if (click.shift()) {
                return ReactiveMenuResult.of(
                        new DragLockState(null, NO_SOURCE_SLOT, null, NO_SOURCE_SLOT, false,
                                "Returned the loaded stack to its source slot."),
                        new ReactiveMenuEffect.SetViewerInventorySlot(state.storedSourceSlot(), state.stored()));
            }
            return ReactiveMenuResult.stay(new DragLockState(null, NO_SOURCE_SLOT, state.stored(), state.storedSourceSlot(), false,
                    "Picked the loaded stack back up from the center slot."));
        }
        if (input instanceof ReactiveMenuInput.DropCursor) {
            if (state.cursor() != null) {
                return ReactiveMenuResult.of(
                        new DragLockState(state.stored(), state.storedSourceSlot(), null, NO_SOURCE_SLOT, state.locked(),
                                "Returned the loaded stack to its source slot."),
                        new ReactiveMenuEffect.SetViewerInventorySlot(state.cursorSourceSlot(), state.cursor()));
            }
            return ReactiveMenuResult.stay(state);
        }
        return ReactiveMenuResult.stay(state);
    }

    private ReactiveMenuResult<ClickLockState> reduceClickLockState(ClickLockState state, ReactiveMenuInput input) {
        if (input instanceof ReactiveMenuInput.Click click && TOGGLE_LOCK.equals(click.message())) {
            boolean locked = !state.locked();
            return ReactiveMenuResult.stay(new ClickLockState(state.stored(), state.storedSourceSlot(), locked,
                    locked ? "Locked the center slot." : "Unlocked the center slot."));
        }
        if (input instanceof ReactiveMenuInput.InventoryClick click && click.item() != null) {
            if (state.locked()) {
                return ReactiveMenuResult.stay(new ClickLockState(state.stored(), state.storedSourceSlot(), true,
                        "Unlock the center slot before loading or returning stacks."));
            }
            if (state.stored() != null) {
                return ReactiveMenuResult.stay(new ClickLockState(state.stored(), state.storedSourceSlot(), false,
                        "The center slot already holds a stack."));
            }
            return ReactiveMenuResult.of(
                    new ClickLockState(click.item(), click.slot(), false, "Loaded the clicked inventory stack into the center slot."),
                    new ReactiveMenuEffect.SetViewerInventorySlot(click.slot(), null));
        }
        if (input instanceof ReactiveMenuInput.Click click && click.slot() == TRUE_CANVAS_CENTER_SLOT) {
            if (state.stored() == null) {
                return ReactiveMenuResult.stay(new ClickLockState(null, NO_SOURCE_SLOT, state.locked(), "The center slot is empty."));
            }
            if (state.locked()) {
                return ReactiveMenuResult.stay(new ClickLockState(state.stored(), state.storedSourceSlot(), true,
                        "Unlock the center slot before returning it."));
            }
            return ReactiveMenuResult.of(
                    new ClickLockState(null, NO_SOURCE_SLOT, false, "Returned the loaded stack to its source slot."),
                    new ReactiveMenuEffect.SetViewerInventorySlot(state.storedSourceSlot(), state.stored()));
        }
        return ReactiveMenuResult.stay(state);
    }

    private SnakeState nextSnakeState(SnakeState state) {
        long seed = state.seed() * 6364136223846793005L + 1442695040888963407L;
        List<Integer> neighbors = snakeNeighbors(state.slot(), state.previousSlot());
        if (neighbors.isEmpty()) {
            return new SnakeState(state.slot(), state.previousSlot(), seed);
        }
        int nextIndex = (int) Math.floorMod(seed, neighbors.size());
        int nextSlot = neighbors.get(nextIndex);
        return new SnakeState(nextSlot, state.slot(), seed);
    }

    private List<Integer> snakeNeighbors(int slot, int previousSlot) {
        List<Integer> neighbors = orthogonalSnakeNeighbors(slot);
        if (neighbors.size() <= 1 || previousSlot == NO_PREVIOUS_SNAKE_SLOT) {
            return neighbors;
        }
        List<Integer> filtered = new ArrayList<>(neighbors);
        filtered.removeIf(candidate -> candidate == previousSlot);
        return filtered.isEmpty() ? neighbors : filtered;
    }

    private List<Integer> orthogonalSnakeNeighbors(int slot) {
        int row = slot / SNAKE_BOARD_COLUMNS;
        int column = slot % SNAKE_BOARD_COLUMNS;
        List<Integer> neighbors = new ArrayList<>(4);
        addSnakeNeighbor(neighbors, row - 1, column);
        addSnakeNeighbor(neighbors, row + 1, column);
        addSnakeNeighbor(neighbors, row, column - 1);
        addSnakeNeighbor(neighbors, row, column + 1);
        return neighbors;
    }

    private void addSnakeNeighbor(List<Integer> neighbors, int row, int column) {
        if (row < 0 || row >= SNAKE_BOARD_ROWS || column < 0 || column >= SNAKE_BOARD_COLUMNS) {
            return;
        }
        int slot = row * SNAKE_BOARD_COLUMNS + column;
        if (slot == TRUE_CANVAS_CENTER_SLOT || slot >= SNAKE_BOARD_LIMIT) {
            return;
        }
        neighbors.add(slot);
    }

    private MenuItem dragLockTarget(DragLockState state) {
        if (state.stored() != null) {
            return state.stored();
        }
        return dragLockEmptyDisplay;
    }

    private MenuDisplayItem dragLockStatus(DragLockState state) {
        return menus.display(state.locked() ? Material.REDSTONE_TORCH : Material.LIME_DYE)
                .name(state.locked()
                        ? FakeSkyBlockMenuTitles.locked("Slot Locked")
                        : FakeSkyBlockMenuTitles.success("Slot Unlocked"))
                .description(state.status())
                .pairs(
                        MenuPair.of("Stored", state.stored() == null
                                ? FakeSkyBlockMenuValues.inactive("Empty")
                                : FakeSkyBlockMenuValues.ready("Loaded")),
                        MenuPair.of("Stored Source", sourceSlotValue(state.storedSourceSlot())),
                        MenuPair.of("Cursor", state.cursor() == null
                                ? FakeSkyBlockMenuValues.inactive("Empty")
                                : FakeSkyBlockMenuValues.tracked("Holding a stack")),
                        MenuPair.of("Cursor Source", sourceSlotValue(state.cursorSourceSlot())))
                .build();
    }

    private MenuItem clickLockTarget(ClickLockState state) {
        if (state.stored() != null) {
            return state.stored();
        }
        return clickLockEmptyDisplay;
    }

    private MenuDisplayItem clickLockStatus(ClickLockState state) {
        return menus.display(state.locked() ? Material.REDSTONE_TORCH : Material.LIME_DYE)
                .name(state.locked()
                        ? FakeSkyBlockMenuTitles.locked("Slot Locked")
                        : FakeSkyBlockMenuTitles.success("Slot Unlocked"))
                .description(state.status())
                .pairs(
                        MenuPair.of("Stored", state.stored() == null
                                ? FakeSkyBlockMenuValues.inactive("Empty")
                                : FakeSkyBlockMenuValues.ready("Loaded")),
                        MenuPair.of("Source Slot", sourceSlotValue(state.storedSourceSlot())),
                        MenuPair.of("Mode", FakeSkyBlockMenuValues.tracked("Click to load / click to return")))
                .build();
    }

    private MenuButton lockToggleButton() {
        return menus.button(Material.LEVER)
                .name(FakeSkyBlockMenuTitles.normal("Toggle Lock"))
                .description("Lock or unlock the center slot without rebuilding the menu.")
                .emit(ActionVerb.TOGGLE, TOGGLE_LOCK)
                .build();
    }

    private MenuButton yourSkyBlockProfileButton() {
        return menus.button(Material.PLAYER_HEAD)
                .name(FakeSkyBlockMenuTitles.normal("Your SkyBlock Profile"))
                .description("View your equipment, stats, and more!")
                .valueLines(FakeSkyBlockMenuValues.profileStats())
                .line("Also accessible via /stats")
                .action(ActionVerb.VIEW, context -> context.open(profilePreviewMenu))
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
                .action(ActionVerb.VIEW, context -> context.open(farmingPreviewMenu))
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
                .action(ActionVerb.VIEW, context -> context.open(museumPreviewMenu))
                .build();
    }

    private static Object sourceSlotValue(int slot) {
        return slot < 0 ? FakeSkyBlockMenuValues.inactive("None") : FakeSkyBlockMenuValues.tracked("Slot " + slot);
    }

    private ReactiveMenuResult<DragLockState> placeCursorIntoViewerSlot(DragLockState state, int slot, String success) {
        if (state.cursor() == null) {
            return ReactiveMenuResult.stay(state);
        }
        return ReactiveMenuResult.of(
                new DragLockState(state.stored(), state.storedSourceSlot(), null, NO_SOURCE_SLOT, state.locked(), success),
                new ReactiveMenuEffect.SetViewerInventorySlot(slot, state.cursor()));
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
                .action(ActionVerb.OPEN, context -> context.open(slotFivePreviewMenu))
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

    private MenuButton reactiveLaunchButton(
            Material material,
            ComponentLike name,
            String description,
            ActionVerb verb,
            MenuDefinition target,
            MenuPair... details
    ) {
        var builder = menus.button(material)
                .name(name)
                .description(description);
        if (details.length > 0) {
            builder.pairs(details);
        }
        return builder.action(verb, context -> context.open(target)).build();
    }

    private MenuButton openMenuButton(
            Material material,
            ComponentLike name,
            String description,
            ActionVerb verb,
            MenuDefinition target
    ) {
        return menus.button(material)
                .name(name)
                .description(description)
                .action(verb, context -> context.open(target))
                .build();
    }

    private Menu preview(String title, MenuDisplayItem item) {
        return menus.canvas()
                .title(title)
                .place(TRUE_CANVAS_CENTER_SLOT, item)
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

    private record SnakeState(int slot, int previousSlot, long seed) {
    }

    private record ReactiveListState(int pageIndex, int highlightedIndex) {
    }

    private record ReactiveTabsState(String activeTabId, int navStart, int pageIndex, int focusedIndex) {
    }

    private record DragLockState(MenuStack stored, int storedSourceSlot, MenuStack cursor, int cursorSourceSlot,
                                 boolean locked, String status) {
    }

    private record ClickLockState(MenuStack stored, int storedSourceSlot, boolean locked, String status) {
    }
}
