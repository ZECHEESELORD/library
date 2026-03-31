package sh.harold.creative.library.example.minestom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.minestom.server.item.Material;
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
import sh.harold.creative.library.menu.ReactiveMenuEffect;
import sh.harold.creative.library.menu.ReactiveMenu;
import sh.harold.creative.library.menu.ReactiveMenuInput;
import sh.harold.creative.library.menu.ReactiveMenuResult;
import sh.harold.creative.library.menu.ReactiveMenuView;
import sh.harold.creative.library.menu.UtilitySlot;
import sh.harold.creative.library.menu.minestom.MinestomMenuPlatform;
import sh.harold.creative.library.sound.SoundCueKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

final class MinestomMenuExampleMenus {

    static final String LOCK_DRAG_TITLE = "Reactive Lock Demo";
    static final String LOCK_CLICK_TITLE = "Reactive Click Demo";

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
    private static final int NO_VIEWER_SLOT = -1;

    private final MinestomMenuPlatform menus;
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
    private final ReactiveMenu snakeDemoMenu;
    private final ReactiveMenu lockDragDemoMenu;
    private final ReactiveMenu lockClickDemoMenu;
    private final Menu listGalleryMenu;
    private final Menu canvasGalleryMenu;
    private final Menu reactiveGalleryMenu;
    private final Menu tabsGalleryMenu;

    MinestomMenuExampleMenus(MinestomMenuPlatform menus) {
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
                                        "Jump between the compiled list gallery, the reactive demo gallery, and the true fixed-slot canvas gallery from one route tab.",
                                        builder -> {
                                            builder.place(TAB_CANVAS_LEFT_SLOT, openMenuButton(Material.CHEST, FakeSkyBlockMenuTitles.normal("Open List Gallery"),
                                                    "Jump into the plain paged list example.", ActionVerb.OPEN, listGalleryMenu));
                                            builder.place(TAB_CANVAS_CENTER_SLOT, openMenuButton(Material.SLIME_BALL, FakeSkyBlockMenuTitles.special("Open Reactive Gallery"),
                                                    "Open the routed reactive demos for ticking, drag, and inventory-click testing.", ActionVerb.OPEN, reactiveGalleryMenu));
                                            builder.place(TAB_CANVAS_RIGHT_SLOT, openMenuButton(Material.ITEM_FRAME, FakeSkyBlockMenuTitles.normal("Open True Canvas Gallery"),
                                                    "Jump into the true fixed-slot canvas example centered on row 2.", ActionVerb.OPEN, canvasGalleryMenu));
                                        },
                                        "Open paged list gallery",
                                        "Open reactive demo gallery",
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
                        reactiveLaunchButton(Material.SLIME_BALL, FakeSkyBlockMenuTitles.special("Reactive Snake"),
                                "Watch a one-slot snake wander the menu grid one orthogonal step at a time while the runtime patches only the slots that changed.",
                                ActionVerb.OPEN,
                                snakeDemoMenu,
                                MenuPair.of("Mode", FakeSkyBlockMenuValues.tracked("Ticked diff updates")),
                                MenuPair.of("Shape", FakeSkyBlockMenuValues.ready("One-slot orthogonal walk"))),
                        reactiveLaunchButton(Material.HOPPER, FakeSkyBlockMenuTitles.perk("Lockable Shift & Drag Slot"),
                                "Move a real inventory stack into the open center slot by click, drag, or shift-click, then return it without duplication.",
                                ActionVerb.OPEN,
                                lockDragDemoMenu,
                                MenuPair.of("Mode", FakeSkyBlockMenuValues.tracked("Owned transfer flow")),
                                MenuPair.of("Focus", FakeSkyBlockMenuValues.ready("Shift, click, drag, and return"))),
                        reactiveLaunchButton(Material.CHEST, FakeSkyBlockMenuTitles.normal("Lockable Inventory Click Slot"),
                                "Click an inventory stack to move it into the center slot, then click it again to return it to the original slot.",
                                ActionVerb.OPEN,
                                lockClickDemoMenu,
                                MenuPair.of("Mode", FakeSkyBlockMenuValues.tracked("Direct slot transfer")),
                                MenuPair.of("Focus", FakeSkyBlockMenuValues.ready("No-cursor insert and return")))))
                .build();
    }

    private ReactiveMenu buildSnakeDemo() {
        return menus.reactive()
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
        return menus.reactive()
                .utility(UtilitySlot.LEFT_1, lockToggleUtilityButton)
                .state(new DragLockState(null, NO_VIEWER_SLOT, null, NO_VIEWER_SLOT, false,
                        "Click an inventory stack to pick it up, or shift-click one to insert it directly."))
                .render(state -> ReactiveMenuView.builder(LOCK_DRAG_TITLE)
                        .cursor(state.cursor())
                        .place(TRUE_CANVAS_LEFT_SLOT, dragLockInfoDisplay)
                        .place(TRUE_CANVAS_CENTER_SLOT, dragLockTarget(state))
                        .place(TRUE_CANVAS_RIGHT_SLOT, dragLockStatus(state))
                        .build())
                .reduce(this::reduceDragLockState)
                .build();
    }

    private ReactiveMenu buildLockClickDemo() {
        return menus.reactive()
                .utility(UtilitySlot.LEFT_1, lockToggleUtilityButton)
                .state(new ClickLockState(null, NO_VIEWER_SLOT, false,
                        "Click an inventory stack to move it into the center slot."))
                .render(state -> ReactiveMenuView.builder(LOCK_CLICK_TITLE)
                        .place(TRUE_CANVAS_LEFT_SLOT, clickLockInfoDisplay)
                        .place(TRUE_CANVAS_CENTER_SLOT, clickLockTarget(state))
                        .place(TRUE_CANVAS_RIGHT_SLOT, clickLockStatus(state))
                        .build())
                .reduce(this::reduceClickLockState)
                .build();
    }

    private ReactiveMenuResult<DragLockState> reduceDragLockState(DragLockState state, ReactiveMenuInput input) {
        if (input instanceof ReactiveMenuInput.Click click && TOGGLE_LOCK.equals(click.message())) {
            boolean locked = !state.locked();
            return ReactiveMenuResult.stay(new DragLockState(state.stored(), state.storedSourceSlot(), state.cursor(), state.cursorSourceSlot(), locked,
                    locked ? "Locked the center slot." : "Unlocked the center slot."));
        }
        if (input instanceof ReactiveMenuInput.InventoryClick click) {
            if (state.cursor() != null) {
                if (click.item() == null) {
                    return placeCursorIntoViewerSlot(state, click.slot(), "Placed the carried stack into that inventory slot.");
                }
                return ReactiveMenuResult.stay(new DragLockState(state.stored(), state.storedSourceSlot(), state.cursor(), state.cursorSourceSlot(), state.locked(),
                        "Pick an empty inventory slot before placing the carried stack."));
            }
            if (click.item() == null) {
                return ReactiveMenuResult.stay(state);
            }
            if (click.shift()) {
                if (state.locked()) {
                    return ReactiveMenuResult.stay(new DragLockState(state.stored(), state.storedSourceSlot(), state.cursor(), state.cursorSourceSlot(), true,
                            "Unlock the center slot before shift-inserting a stack."));
                }
                if (state.stored() != null) {
                    return ReactiveMenuResult.stay(new DragLockState(state.stored(), state.storedSourceSlot(), state.cursor(), state.cursorSourceSlot(), false,
                            "The center slot already holds a stack."));
                }
                return ReactiveMenuResult.of(
                        new DragLockState(click.item(), click.slot(), state.cursor(), state.cursorSourceSlot(), false,
                                "Shift-clicked the stack directly into the center slot."),
                        new ReactiveMenuEffect.SetViewerInventorySlot(click.slot(), null));
            }
            return ReactiveMenuResult.of(
                    new DragLockState(state.stored(), state.storedSourceSlot(), click.item(), click.slot(), state.locked(),
                            "Picked up the clicked stack from your inventory."),
                    new ReactiveMenuEffect.SetViewerInventorySlot(click.slot(), null));
        }
        if (input instanceof ReactiveMenuInput.Drag drag
                && drag.slots().contains(TRUE_CANVAS_CENTER_SLOT)) {
            return insertFromCursor(state, "Dragged the carried stack onto the center slot.");
        }
        if (input instanceof ReactiveMenuInput.Click click && click.slot() == TRUE_CANVAS_CENTER_SLOT) {
            if (state.cursor() != null) {
                return insertFromCursor(state, "Placed the carried stack into the center slot.");
            }
            if (state.stored() == null) {
                return ReactiveMenuResult.stay(new DragLockState(null, NO_VIEWER_SLOT, null, NO_VIEWER_SLOT, state.locked(),
                        "Pick up a stack from your inventory first."));
            }
            if (state.locked()) {
                return ReactiveMenuResult.stay(new DragLockState(state.stored(), state.storedSourceSlot(), state.cursor(), state.cursorSourceSlot(), true,
                        "Unlock the center slot before removing the stored stack."));
            }
            if (click.shift()) {
                return returnStoredToViewerSlot(state, "Returned the stored stack to its original inventory slot.");
            }
            return ReactiveMenuResult.stay(new DragLockState(null, NO_VIEWER_SLOT, state.stored(), state.storedSourceSlot(), false,
                    "Picked the stored stack back up."));
        }
        if (input instanceof ReactiveMenuInput.DropCursor) {
            if (state.cursor() != null) {
                return returnCursorToViewerSlot(state, "Returned the carried stack to its original inventory slot.");
            }
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
                        "Unlock the center slot before inserting a stack."));
            }
            if (state.stored() != null) {
                return ReactiveMenuResult.stay(new ClickLockState(state.stored(), state.storedSourceSlot(), false,
                        "Return the loaded stack before inserting another one."));
            }
            return ReactiveMenuResult.of(
                    new ClickLockState(click.item(), click.slot(), false,
                            "Moved the clicked inventory stack into the center slot."),
                    new ReactiveMenuEffect.SetViewerInventorySlot(click.slot(), null));
        }
        if (input instanceof ReactiveMenuInput.Click click && click.slot() == TRUE_CANVAS_CENTER_SLOT) {
            if (state.stored() == null) {
                return ReactiveMenuResult.stay(new ClickLockState(null, NO_VIEWER_SLOT, state.locked(),
                        "Click an inventory stack to load it into the center slot."));
            }
            if (state.locked()) {
                return ReactiveMenuResult.stay(new ClickLockState(state.stored(), state.storedSourceSlot(), true,
                        "Unlock the center slot before returning the stored stack."));
            }
            return returnClickStoredToViewerSlot(state, "Returned the stored stack to its original inventory slot.");
        }
        return ReactiveMenuResult.stay(state);
    }

    private ReactiveMenuResult<DragLockState> insertFromCursor(DragLockState state, String success) {
        if (state.cursor() == null) {
            return ReactiveMenuResult.stay(state);
        }
        if (state.locked()) {
            return ReactiveMenuResult.stay(new DragLockState(state.stored(), state.storedSourceSlot(), state.cursor(), state.cursorSourceSlot(), true,
                    "Unlock the center slot before placing the carried stack."));
        }
        if (state.stored() != null) {
            return ReactiveMenuResult.stay(new DragLockState(state.stored(), state.storedSourceSlot(), state.cursor(), state.cursorSourceSlot(), false,
                    "The center slot already holds a stack."));
        }
        return ReactiveMenuResult.stay(new DragLockState(state.cursor(), state.cursorSourceSlot(), null, NO_VIEWER_SLOT, false, success));
    }

    private ReactiveMenuResult<DragLockState> returnStoredToViewerSlot(DragLockState state, String success) {
        if (state.stored() == null || state.storedSourceSlot() == NO_VIEWER_SLOT) {
            return ReactiveMenuResult.stay(state);
        }
        return ReactiveMenuResult.of(
                new DragLockState(null, NO_VIEWER_SLOT, null, NO_VIEWER_SLOT, state.locked(), success),
                new ReactiveMenuEffect.SetViewerInventorySlot(state.storedSourceSlot(), state.stored()));
    }

    private ReactiveMenuResult<DragLockState> returnCursorToViewerSlot(DragLockState state, String success) {
        if (state.cursor() == null || state.cursorSourceSlot() == NO_VIEWER_SLOT) {
            return ReactiveMenuResult.stay(state);
        }
        return ReactiveMenuResult.of(
                new DragLockState(state.stored(), state.storedSourceSlot(), null, NO_VIEWER_SLOT, state.locked(), success),
                new ReactiveMenuEffect.SetViewerInventorySlot(state.cursorSourceSlot(), state.cursor()));
    }

    private ReactiveMenuResult<DragLockState> placeCursorIntoViewerSlot(DragLockState state, int slot, String success) {
        if (state.cursor() == null) {
            return ReactiveMenuResult.stay(state);
        }
        return ReactiveMenuResult.of(
                new DragLockState(state.stored(), state.storedSourceSlot(), null, NO_VIEWER_SLOT, state.locked(), success),
                new ReactiveMenuEffect.SetViewerInventorySlot(slot, state.cursor()));
    }

    private ReactiveMenuResult<ClickLockState> returnClickStoredToViewerSlot(ClickLockState state, String success) {
        if (state.stored() == null || state.storedSourceSlot() == NO_VIEWER_SLOT) {
            return ReactiveMenuResult.stay(state);
        }
        return ReactiveMenuResult.of(
                new ClickLockState(null, NO_VIEWER_SLOT, state.locked(), success),
                new ReactiveMenuEffect.SetViewerInventorySlot(state.storedSourceSlot(), state.stored()));
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
                        MenuPair.of("Cursor", state.cursor() == null
                                ? FakeSkyBlockMenuValues.inactive("Empty")
                                : FakeSkyBlockMenuValues.tracked("Holding a stack")))
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
                        MenuPair.of("Return", state.stored() == null
                                ? FakeSkyBlockMenuValues.inactive("Waiting")
                                : FakeSkyBlockMenuValues.tracked("Original slot")))
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

    private record DragLockState(
            MenuStack stored,
            int storedSourceSlot,
            MenuStack cursor,
            int cursorSourceSlot,
            boolean locked,
            String status
    ) {
    }

    private record ClickLockState(MenuStack stored, int storedSourceSlot, boolean locked, String status) {
    }
}
