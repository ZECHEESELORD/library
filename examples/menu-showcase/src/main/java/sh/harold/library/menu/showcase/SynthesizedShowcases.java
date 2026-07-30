package sh.harold.library.menu.showcase;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import sh.harold.library.menu.ActionVerb;
import sh.harold.library.menu.Menu;
import sh.harold.library.menu.MenuButton;
import sh.harold.library.menu.MenuCustodyDecision;
import sh.harold.library.menu.MenuCustodyDestination;
import sh.harold.library.menu.MenuCustodyGesture;
import sh.harold.library.menu.MenuCustodySnapshot;
import sh.harold.library.menu.MenuDefinition;
import sh.harold.library.menu.MenuDisplayItem;
import sh.harold.library.menu.MenuIcon;
import sh.harold.library.menu.MenuItem;
import sh.harold.library.menu.MenuItemTemplate;
import sh.harold.library.menu.MenuOptionLine;
import sh.harold.library.menu.MenuProgressPalette;
import sh.harold.library.menu.MenuTab;
import sh.harold.library.menu.ReactiveGeometryAction;
import sh.harold.library.menu.ReactiveListControlState;
import sh.harold.library.menu.ReactiveListControls;
import sh.harold.library.menu.ReactiveListView;
import sh.harold.library.menu.ReactiveMenu;
import sh.harold.library.menu.ReactiveMenuInput;
import sh.harold.library.menu.ReactiveMenuResult;
import sh.harold.library.menu.ReactiveMenuView;
import sh.harold.library.menu.ReactiveTabsView;
import sh.harold.library.menu.ReactiveTextPromptRequest;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

final class SynthesizedShowcases {

    private static final String CUSTODY_TARGET = "work-slot";
    private static final ReactiveTextPromptRequest QUEST_SEARCH =
            ReactiveTextPromptRequest.sign("quest-search", "Search quests", "");

    private SynthesizedShowcases() {
    }

    static List<ShowcaseEntry> create(sh.harold.library.menu.MenuService menus) {
        return List.of(
                networkBrowser(menus),
                guildOperations(menus),
                loadoutWorkshop(menus),
                upgradeConfirmation(menus),
                questJournal(menus),
                matchFinder(menus),
                tournamentControl(menus),
                forgeQueue(menus),
                salvageStation(menus),
                mailLocker(menus));
    }

    private static ShowcaseEntry networkBrowser(sh.harold.library.menu.MenuService menus) {
        Menu initial = networkList(menus, -1);
        Menu alternate = networkList(menus, 17);
        return synth("network-browser", "Network Browser", initial, initial, alternate,
                ShowcaseFeature.COMPILED_LIST_PAGING);
    }

    private static Menu networkList(sh.harold.library.menu.MenuService menus, int selected) {
        List<MenuItem> servers = IntStream.rangeClosed(1, 38)
                .mapToObj(index -> (MenuItem) MenuButton.builder(MenuIcon.vanilla("compass"))
                        .name(Component.text("Server " + index,
                                index == selected ? NamedTextColor.YELLOW : NamedTextColor.GREEN))
                        .secondary(index % 3 == 0 ? "Busy" : "Available")
                        .section(section -> section
                                .valueLine("Players: ", Component.text(120 + index * 7, NamedTextColor.AQUA))
                                .valueLine("Mode: ", Component.text(index % 2 == 0 ? "Classic" : "Rotation",
                                        NamedTextColor.WHITE)))
                        .status(index == selected
                                ? Component.text("Currently selected", NamedTextColor.GREEN)
                                : Component.text("Click to connect", NamedTextColor.GRAY))
                        .onLeftClick(ActionVerb.SELECT, "connect", context -> { })
                        .build())
                .toList();
        return menus.list().title("Network Browser").addItems(servers).build();
    }

    private static ShowcaseEntry guildOperations(sh.harold.library.menu.MenuService menus) {
        Menu initial = guildTabs(menus, false);
        Menu alternate = guildTabs(menus, true);
        return synth("guild-operations", "Guild Operations", initial, initial, alternate,
                ShowcaseFeature.LIST_TABS,
                ShowcaseFeature.CANVAS_TABS,
                ShowcaseFeature.OVERFLOWING_TAB_STRIP);
    }

    private static Menu guildTabs(sh.harold.library.menu.MenuService menus, boolean maintenance) {
        var builder = menus.tabs().title("Guild Operations").defaultTab("members");
        builder.addTab(MenuTab.builder("members", MenuIcon.vanilla("player_head"))
                .name("Members")
                .secondary("38 online")
                .items(IntStream.range(0, 30)
                        .mapToObj(index -> (MenuItem) MenuDisplayItem.builder(MenuIcon.vanilla("player_head"))
                                .name(Component.text("Member " + (index + 1), NamedTextColor.GREEN))
                                .valueLine("Contribution: ", Component.text((index + 1) * 250, NamedTextColor.GOLD))
                                .build())
                        .toList())
                .build());
        builder.addTab(MenuTab.builder("overview", MenuIcon.vanilla("beacon"))
                .name("Overview")
                .status(maintenance
                        ? Component.text("Guild services are under maintenance.", NamedTextColor.RED)
                        : Component.text("All guild services operational.", NamedTextColor.GREEN))
                .canvas(canvas -> canvas.place(22,
                        MenuDisplayItem.builder(MenuIcon.vanilla("beacon"))
                                .name(Component.text("Guild Level 42", NamedTextColor.GOLD))
                                .progress("Progress to Level 43", maintenance ? 91 : 67, 100)
                                .build()))
                .build());
        for (int index = 0; index < 8; index++) {
            int tabIndex = index;
            builder.addTab(MenuTab.canvas("ops-" + index, "Ops " + (index + 1),
                    MenuIcon.vanilla("map"), canvas -> canvas.place(22,
                            MenuDisplayItem.builder(MenuIcon.vanilla("map"))
                                    .name(Component.text("Operation " + (tabIndex + 1), NamedTextColor.AQUA))
                                    .description("A fixed operational surface in an overflowing tab strip.")
                                    .build())));
        }
        return builder.build();
    }

    private static ShowcaseEntry loadoutWorkshop(sh.harold.library.menu.MenuService menus) {
        ReactiveMenu runtime = menus.reactiveTabs()
                .stateFactory(() -> new LoadoutState("combat", false))
                .render(SynthesizedShowcases::loadoutView)
                .reduce(SynthesizedShowcases::reduceLoadout)
                .build();
        Menu initial = loadoutSnapshot(menus, false);
        Menu alternate = loadoutSnapshot(menus, true);
        return synth("loadout-workshop", "Loadout Workshop", runtime, initial, alternate,
                ShowcaseFeature.REACTIVE_TABS,
                ShowcaseFeature.DUAL_SHIFT_ACTIONS);
    }

    private static ReactiveTabsView loadoutView(LoadoutState state) {
        return ReactiveTabsView.builder("Loadout Workshop")
                .activeTab(state.activeTab())
                .addTab(MenuTab.of("combat", "Combat", MenuIcon.vanilla("diamond_sword"),
                        List.of(loadoutButton(state.equipped(), true))))
                .addTab(MenuTab.canvas("utility", "Utility", MenuIcon.vanilla("ender_chest"),
                        canvas -> canvas.place(31, loadoutButton(state.equipped(), true))))
                .build();
    }

    private static ReactiveMenuResult<LoadoutState> reduceLoadout(LoadoutState state, ReactiveMenuInput input) {
        if (input instanceof ReactiveMenuInput.Click click) {
            if (click.message() instanceof ReactiveGeometryAction.SwitchTab tab) {
                return ReactiveMenuResult.update(new LoadoutState(tab.tabId(), state.equipped()));
            }
            if ("toggle-loadout".equals(click.message())) {
                return ReactiveMenuResult.update(new LoadoutState(state.activeTab(), !state.equipped()));
            }
        }
        return ReactiveMenuResult.unchanged();
    }

    private static Menu loadoutSnapshot(sh.harold.library.menu.MenuService menus, boolean equipped) {
        return menus.tabs()
                .title("Loadout Workshop")
                .defaultTab("combat")
                .addTab(MenuTab.of("combat", "Combat", MenuIcon.vanilla("diamond_sword"),
                        List.of(loadoutButton(equipped, false))))
                .addTab(MenuTab.canvas("utility", "Utility", MenuIcon.vanilla("ender_chest"),
                        canvas -> canvas.place(31, loadoutButton(equipped, false))))
                .build();
    }

    private static MenuButton loadoutButton(boolean equipped, boolean reactive) {
        MenuButton.Builder builder = MenuButton.builder(MenuIcon.vanilla("diamond_chestplate"))
                .name(Component.text("Vanguard Loadout", equipped ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                .description("A reusable combat configuration with four intentional gestures.")
                .status(Component.text(equipped ? "Equipped" : "Stored",
                        equipped ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        if (reactive) {
            builder.emit(ActionVerb.SELECT, "equip", "toggle-loadout");
        } else {
            builder.onLeftClick(ActionVerb.SELECT, "equip", context -> { });
        }
        return builder
                .onShiftLeftClick(ActionVerb.CONFIRM, "overwrite", context -> { })
                .onRightClick(ActionVerb.VIEW, "preview", context -> { })
                .onShiftRightClick(ActionVerb.SELECT, "rename", context -> { })
                .build();
    }

    private static ShowcaseEntry upgradeConfirmation(sh.harold.library.menu.MenuService menus) {
        Menu initial = upgradeConfirmationMenu(menus, false);
        Menu alternate = upgradeConfirmationMenu(menus, true);
        return synth("upgrade-confirmation", "Upgrade Confirmation", initial, initial, alternate,
                ShowcaseFeature.CONFIRMATION);
    }

    private static Menu upgradeConfirmationMenu(sh.harold.library.menu.MenuService menus, boolean affordable) {
        MenuDisplayItem info = MenuDisplayItem.builder(MenuIcon.vanilla("anvil"))
                .name(Component.text("Upgrade Reactor", NamedTextColor.GOLD))
                .description("Permanently raises the reactor capacity by 20%.")
                .section(section -> section
                        .valueLine("Cost: ", Component.text("125,000 coins", NamedTextColor.GOLD))
                        .valueLine("Balance: ", Component.text(affordable ? "180,000 coins" : "80,000 coins",
                                affordable ? NamedTextColor.GREEN : NamedTextColor.RED)))
                .status(Component.text(affordable ? "Ready to upgrade!" : "You cannot afford this!",
                        affordable ? NamedTextColor.GREEN : NamedTextColor.RED))
                .build();
        MenuButton confirm = MenuButton.builder(MenuIcon.vanilla(
                        affordable ? "green_terracotta" : "gray_terracotta"))
                .name(Component.text("Confirm", affordable ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .status(affordable
                        ? Component.text("Upgrade immediately.", NamedTextColor.GRAY)
                        : Component.text("Insufficient funds.", NamedTextColor.RED))
                .onLeftClick(ActionVerb.CONFIRM, context -> { })
                .build();
        MenuButton cancel = MenuButton.builder(MenuIcon.vanilla("red_terracotta"))
                .name(Component.text("Cancel", NamedTextColor.RED))
                .onLeftClick(ActionVerb.BACK, "cancel", context -> context.back())
                .build();
        return menus.confirmation()
                .title("Confirm Upgrade")
                .info(info)
                .cancel(cancel)
                .confirm(confirm)
                .build();
    }

    private static ShowcaseEntry questJournal(sh.harold.library.menu.MenuService menus) {
        ReactiveMenu runtime = menus.reactiveList()
                .stateFactory(QuestState::new)
                .render(SynthesizedShowcases::questView)
                .reduce(SynthesizedShowcases::reduceQuests)
                .build();
        QuestState initialState = new QuestState();
        QuestState filteredState = new QuestState();
        filteredState.controls().searchQuery("forge");
        filteredState.controls().filterIndex(1);
        Menu initial = questSnapshot(menus, initialState);
        Menu alternate = questSnapshot(menus, filteredState);
        return synth("quest-journal", "Quest Journal", runtime, initial, alternate,
                ShowcaseFeature.REACTIVE_LIST_CONTROLS,
                ShowcaseFeature.REACTIVE_PROMPTS);
    }

    private static ReactiveListView questView(QuestState state) {
        return ReactiveListView.builder("Quest Journal")
                .page(state.page())
                .addItems(filteredQuests(state))
                .utility(ReactiveListControls.SEARCH_SLOT,
                        ReactiveListControls.searchButton(
                                "Find quests by title or region.", state.controls().searchQuery()))
                .utility(ReactiveListControls.FILTER_SLOT,
                        ReactiveListControls.filterButton(
                                "Choose which quest states are visible.", questFilterOptions(state)))
                .utility(ReactiveListControls.SORT_SLOT,
                        ReactiveListControls.sortButton(
                                "Choose the journal ordering.", questSortOptions(state)))
                .build();
    }

    private static ReactiveMenuResult<QuestState> reduceQuests(QuestState state, ReactiveMenuInput input) {
        if (input instanceof ReactiveMenuInput.Click click) {
            if (click.message() instanceof ReactiveGeometryAction.PreviousPage) {
                state.page(Math.max(0, state.page() - 1));
                return ReactiveMenuResult.update(state);
            }
            if (click.message() instanceof ReactiveGeometryAction.NextPage) {
                state.page(state.page() + 1);
                return ReactiveMenuResult.update(state);
            }
        }
        ReactiveListControls.Update update = ReactiveListControls.reduce(
                state.controls(), input, QUEST_SEARCH, 2, 2);
        if (update.effect().isPresent()) {
            return ReactiveMenuResult.effect(update.effect().orElseThrow());
        }
        if (update.changed()) {
            state.page(0);
            return ReactiveMenuResult.update(state);
        }
        return ReactiveMenuResult.unchanged();
    }

    private static Menu questSnapshot(sh.harold.library.menu.MenuService menus, QuestState state) {
        return menus.list()
                .title("Quest Journal")
                .addItems(filteredQuests(state))
                .utility(ReactiveListControls.SEARCH_SLOT, snapshotSearchButton(state.controls().searchQuery()))
                .utility(ReactiveListControls.FILTER_SLOT, snapshotCycleButton(
                        "Filter", "hopper", NamedTextColor.GOLD,
                        "Choose which quest states are visible.", questFilterOptions(state)))
                .utility(ReactiveListControls.SORT_SLOT, snapshotCycleButton(
                        "Sort", "comparator", NamedTextColor.AQUA,
                        "Choose the journal ordering.", questSortOptions(state)))
                .build();
    }

    private static MenuButton snapshotSearchButton(String query) {
        MenuButton.Builder builder = MenuButton.builder(MenuIcon.vanilla("oak_sign"))
                .name(Component.text("Search", NamedTextColor.GREEN))
                .description("Find quests by title or region.")
                .onLeftClick(ActionVerb.BROWSE, "search effects", context -> { })
                .onRightClick(ActionVerb.BROWSE, "clear search", context -> { });
        if (!query.isBlank()) {
            builder.valueLine("Filtered: ", Component.text(query, NamedTextColor.YELLOW));
        }
        return builder.build();
    }

    private static MenuButton snapshotCycleButton(
            String title,
            String icon,
            NamedTextColor color,
            String description,
            List<MenuOptionLine> options
    ) {
        return MenuButton.builder(MenuIcon.vanilla(icon))
                .name(Component.text(title, color))
                .description(description)
                .options(options)
                .onLeftClick(ActionVerb.BROWSE, "cycle " + title.toLowerCase() + " forward", context -> { })
                .onRightClick(ActionVerb.BROWSE, "cycle " + title.toLowerCase() + " backward", context -> { })
                .build();
    }

    private static List<MenuItem> filteredQuests(QuestState state) {
        List<Quest> quests = List.of(
                new Quest("Repair the Forge", "Foundry", false),
                new Quest("Scout the Ruins", "Highlands", true),
                new Quest("Deliver the Charter", "Capital", false),
                new Quest("Recover Lost Mail", "Harbor", true),
                new Quest("Stabilize the Portal", "Foundry", false));
        return quests.stream()
                .filter(quest -> state.controls().filterIndex() == 0 || !quest.complete())
                .filter(quest -> ReactiveListControls.matchesSearch(
                        state.controls().searchQuery(), List.of(quest.name(), quest.region())))
                .sorted(state.controls().sortIndex() == 0
                        ? Comparator.comparing(Quest::name)
                        : Comparator.comparing(Quest::region))
                .map(quest -> (MenuItem) MenuButton.builder(MenuIcon.vanilla(
                                quest.complete() ? "lime_dye" : "writable_book"))
                        .name(Component.text(quest.name(),
                                quest.complete() ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                        .valueLine("Region: ", Component.text(quest.region(), NamedTextColor.AQUA))
                        .status(Component.text(quest.complete() ? "Complete" : "In progress",
                                quest.complete() ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                        .onLeftClick(ActionVerb.VIEW, context -> { })
                        .build())
                .toList();
    }

    private static List<MenuOptionLine> questFilterOptions(QuestState state) {
        return List.of(
                new MenuOptionLine("All quests", NamedTextColor.YELLOW, state.controls().filterIndex() == 0),
                new MenuOptionLine("Active only", NamedTextColor.GREEN, state.controls().filterIndex() == 1));
    }

    private static List<MenuOptionLine> questSortOptions(QuestState state) {
        return List.of(
                new MenuOptionLine("Name", NamedTextColor.AQUA, state.controls().sortIndex() == 0),
                new MenuOptionLine("Region", NamedTextColor.GOLD, state.controls().sortIndex() == 1));
    }

    private static ShowcaseEntry matchFinder(sh.harold.library.menu.MenuService menus) {
        ReactiveMenu runtime = menus.reactiveCanvas()
                .tickEvery(20)
                .stateFactory(() -> new MatchState(0))
                .render(state -> matchView(state.seconds()))
                .reduce((state, input) -> input instanceof ReactiveMenuInput.Tick
                        ? ReactiveMenuResult.update(new MatchState(state.seconds() + 1))
                        : ReactiveMenuResult.unchanged())
                .build();
        Menu initial = matchSnapshot(menus, 0);
        Menu alternate = matchSnapshot(menus, 18);
        return synth("match-finder", "Match Finder", runtime, initial, alternate,
                ShowcaseFeature.TICKING_CANVAS);
    }

    private static ReactiveMenuView matchView(int seconds) {
        return ReactiveMenuView.builder("Match Finder")
                .place(22, matchStatus(seconds))
                .build();
    }

    private static Menu matchSnapshot(sh.harold.library.menu.MenuService menus, int seconds) {
        return menus.canvas().title("Match Finder").place(22, matchStatus(seconds)).build();
    }

    private static MenuDisplayItem matchStatus(int seconds) {
        return MenuDisplayItem.builder(MenuIcon.vanilla("clock"))
                .name(Component.text("Searching for a match...", NamedTextColor.YELLOW))
                .valueLine("Elapsed: ", Component.text(seconds + "s", NamedTextColor.AQUA))
                .progress("Estimated wait", Math.min(seconds, 30), 30, "seconds", MenuProgressPalette.AQUA)
                .build();
    }

    private static ShowcaseEntry tournamentControl(sh.harold.library.menu.MenuService menus) {
        Menu initial = tournamentMenu(menus, false);
        Menu alternate = tournamentMenu(menus, true);
        return synth("tournament-control", "Tournament Control", initial, initial, alternate,
                ShowcaseFeature.FIXED_CANVAS,
                ShowcaseFeature.CUSTOM_FOOTER,
                ShowcaseFeature.LITERAL_PRESENTATION);
    }

    private static Menu tournamentMenu(sh.harold.library.menu.MenuService menus, boolean live) {
        MenuDisplayItem nativeBadge = MenuDisplayItem.builder(MenuIcon.vanilla("nether_star"))
                .exactName(Component.text("Imported Tournament Badge", NamedTextColor.LIGHT_PURPLE))
                .exactLore(
                        Component.text("Native presentation preserved", NamedTextColor.DARK_PURPLE),
                        Component.text(live ? "Round is live" : "Round is staged", NamedTextColor.GRAY))
                .literalItem()
                .glow()
                .build();
        return menus.tabs()
                .title("Tournament Control")
                .customFooter()
                .defaultTab("bracket")
                .addTab(MenuTab.canvas("bracket", "Bracket", MenuIcon.vanilla("map"),
                        canvas -> canvas
                                .place(22, nativeBadge)
                                .place(49, closeButton())))
                .addTab(MenuTab.canvas("round", "Round", MenuIcon.vanilla("clock"),
                        canvas -> canvas
                                .place(22, MenuDisplayItem.builder(MenuIcon.vanilla("clock"))
                                        .name(Component.text(live ? "Round Live" : "Round Ready",
                                                live ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                                        .status(Component.text(
                                                live ? "Results are being recorded." : "Awaiting operator.",
                                                NamedTextColor.GRAY))
                                        .build())
                                .place(49, closeButton())))
                .build();
    }

    private static ShowcaseEntry forgeQueue(sh.harold.library.menu.MenuService menus) {
        Menu initial = forgeMenu(menus, ForgeStage.QUEUED);
        Menu alternate = forgeMenu(menus, ForgeStage.READY);
        return synth("forge-queue", "Forge Queue", initial, initial, alternate,
                ShowcaseFeature.ITEM_TEMPLATES);
    }

    private static Menu forgeMenu(sh.harold.library.menu.MenuService menus, ForgeStage stage) {
        MenuItemTemplate<ForgeState> template = MenuItemTemplate
                .<ForgeState, ForgeStage>builder(MenuIcon.vanilla("blast_furnace"), ForgeState::stage)
                .base((state, item) -> item
                        .name("Titanium Drill")
                        .description("A queued forge job authored once and rendered through keyed states.")
                        .section(section -> section
                                .valueLine("Slot: ", Component.text("#2", NamedTextColor.AQUA))
                                .valueLine("Owner: ", Component.text("Builder", NamedTextColor.WHITE))))
                .variant(ForgeStage.QUEUED, (state, item) -> item
                        .progress("Forge progress", 34, 100)
                        .status(Component.text("2h 18m remaining", NamedTextColor.YELLOW)))
                .variant(ForgeStage.READY, (state, item) -> item
                        .glow()
                        .status(Component.text("Ready to collect!", NamedTextColor.GREEN))
                        .onLeftClick(ActionVerb.CLAIM, context -> { }))
                .build();
        return menus.canvas()
                .title("Forge Queue")
                .place(22, template.render(new ForgeState(stage)))
                .build();
    }

    private static ShowcaseEntry salvageStation(sh.harold.library.menu.MenuService menus) {
        ReactiveMenu runtime = custodyMenu(menus, true);
        Menu initial = custodySnapshot(menus, "Salvage Station", false,
                "Drag or shift-click an item into the salvage slot.");
        Menu alternate = custodySnapshot(menus, "Salvage Station", true,
                "The salvage slot currently owns one native stack.");
        return synth("salvage-station", "Salvage Station", runtime, initial, alternate,
                ShowcaseFeature.DRAG_CUSTODY);
    }

    private static ShowcaseEntry mailLocker(sh.harold.library.menu.MenuService menus) {
        ReactiveMenu runtime = custodyMenu(menus, false);
        Menu initial = custodySnapshot(menus, "Mail Locker", false,
                "Click an inventory stack to attach it to the parcel.");
        Menu alternate = custodySnapshot(menus, "Mail Locker", true,
                "Click the attachment to return it to its source slot.");
        return synth("mail-locker", "Mail Locker", runtime, initial, alternate,
                ShowcaseFeature.CLICK_CUSTODY);
    }

    private static ReactiveMenu custodyMenu(sh.harold.library.menu.MenuService menus, boolean dragWorkflow) {
        String title = dragWorkflow ? "Salvage Station" : "Mail Locker";
        return menus.reactiveCanvas()
                .stateFactory(() -> new CustodyState(false, dragWorkflow
                        ? "Drag or shift-click an item into the salvage slot."
                        : "Click an inventory stack to attach it to the parcel."))
                .custodyTarget(CUSTODY_TARGET, 22)
                .custodyPolicy((state, gesture, snapshot) -> dragWorkflow
                        ? decideDragCustody(gesture, snapshot)
                        : decideClickCustody(gesture, snapshot))
                .render(state -> ReactiveMenuView.builder(title)
                        .place(20, custodyInfo(title, state.status()))
                        .place(22, MenuDisplayItem.builder(MenuIcon.vanilla("stone_button"))
                                .name(Component.text("Work Slot", NamedTextColor.YELLOW))
                                .status(Component.text(state.occupied() ? "Occupied" : "Empty",
                                        state.occupied() ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.CustodyCommitted committed) {
                        boolean occupied = committed.snapshot().targets().containsKey(CUSTODY_TARGET);
                        return ReactiveMenuResult.update(new CustodyState(
                                occupied, occupied ? "Stack accepted." : "Stack returned."));
                    }
                    if (input instanceof ReactiveMenuInput.CustodyRejected) {
                        return ReactiveMenuResult.update(
                                new CustodyState(state.occupied(), "That move is not allowed."));
                    }
                    return ReactiveMenuResult.unchanged();
                })
                .build();
    }

    private static MenuCustodyDecision decideDragCustody(
            MenuCustodyGesture gesture,
            MenuCustodySnapshot snapshot
    ) {
        boolean targetOccupied = snapshot.targets().containsKey(CUSTODY_TARGET);
        boolean cursorOccupied = snapshot.cursor().isPresent();
        if (gesture instanceof MenuCustodyGesture.ViewerClick click && click.slot().item() != null) {
            if (click.shift() && !targetOccupied) {
                return MenuCustodyDecision.move(MenuCustodyDestination.target(CUSTODY_TARGET));
            }
            if (!cursorOccupied) {
                return MenuCustodyDecision.move(MenuCustodyDestination.cursor());
            }
        }
        if (gesture instanceof MenuCustodyGesture.TargetClick && targetOccupied && !cursorOccupied) {
            return MenuCustodyDecision.move(MenuCustodyDestination.origin());
        }
        if (gesture instanceof MenuCustodyGesture.TargetDrag drag
                && drag.targetKeys().equals(List.of(CUSTODY_TARGET))
                && cursorOccupied
                && !targetOccupied) {
            return MenuCustodyDecision.move(MenuCustodyDestination.target(CUSTODY_TARGET));
        }
        if (gesture instanceof MenuCustodyGesture.Settle) {
            return MenuCustodyDecision.move(MenuCustodyDestination.origin());
        }
        return MenuCustodyDecision.reject();
    }

    private static MenuCustodyDecision decideClickCustody(
            MenuCustodyGesture gesture,
            MenuCustodySnapshot snapshot
    ) {
        boolean occupied = snapshot.targets().containsKey(CUSTODY_TARGET);
        if (gesture instanceof MenuCustodyGesture.ViewerClick click
                && click.slot().item() != null
                && !occupied
                && snapshot.cursor().isEmpty()) {
            return MenuCustodyDecision.move(MenuCustodyDestination.target(CUSTODY_TARGET));
        }
        if (gesture instanceof MenuCustodyGesture.TargetClick
                && occupied
                && snapshot.cursor().isEmpty()) {
            return MenuCustodyDecision.move(MenuCustodyDestination.origin());
        }
        if (gesture instanceof MenuCustodyGesture.Settle) {
            return MenuCustodyDecision.move(MenuCustodyDestination.origin());
        }
        return MenuCustodyDecision.reject();
    }

    private static Menu custodySnapshot(
            sh.harold.library.menu.MenuService menus,
            String title,
            boolean occupied,
            String status
    ) {
        return menus.canvas()
                .title(title)
                .place(20, custodyInfo(title, status))
                .place(22, MenuDisplayItem.builder(MenuIcon.vanilla(occupied ? "chest" : "stone_button"))
                        .name(Component.text("Work Slot", NamedTextColor.YELLOW))
                        .status(Component.text(occupied ? "Occupied" : "Empty",
                                occupied ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                        .build())
                .build();
    }

    private static MenuDisplayItem custodyInfo(String title, String status) {
        return MenuDisplayItem.builder(MenuIcon.vanilla(
                        title.equals("Salvage Station") ? "grindstone" : "chest"))
                .name(Component.text(title, NamedTextColor.AQUA))
                .description(status)
                .build();
    }

    private static MenuButton closeButton() {
        return MenuButton.builder(MenuIcon.vanilla("barrier"))
                .name(Component.text("Close", NamedTextColor.RED))
                .onLeftClick(ActionVerb.CLOSE, context -> context.close())
                .build();
    }

    private static ShowcaseEntry synth(
            String id,
            String label,
            MenuDefinition runtime,
            Menu initial,
            Menu alternate,
            ShowcaseFeature... features
    ) {
        return new ShowcaseEntry(
                id,
                label,
                ShowcaseOrigin.SYNTHESIZED,
                Optional.empty(),
                runtime,
                List.of(
                        new ShowcaseSnapshot("initial", initial),
                        new ShowcaseSnapshot("alternate", alternate)),
                Set.of(features));
    }

    private record LoadoutState(String activeTab, boolean equipped) {
    }

    private record MatchState(int seconds) {
    }

    private record Quest(String name, String region, boolean complete) {
    }

    private static final class QuestState {

        private final ReactiveListControlState controls = new ReactiveListControlState();
        private int page;

        private ReactiveListControlState controls() {
            return controls;
        }

        private int page() {
            return page;
        }

        private void page(int page) {
            this.page = Math.max(0, page);
        }
    }

    private enum ForgeStage {
        QUEUED,
        READY
    }

    private record ForgeState(ForgeStage stage) {
    }

    private record CustodyState(boolean occupied, String status) {
    }
}
