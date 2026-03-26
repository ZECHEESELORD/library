# Menu Runtime

The shared menu stack now has a real runtime on both supported hosts:

- `platform:paper:menu-paper`
- `platform:minestom:menu-minestom`

Velocity remains intentionally unsupported for menus.

## What Works

- `list`, `tabs`, and `canvas`
- real `open(viewer, menu)` on Paper and Minestom
- per-viewer menu sessions
- click routing through owned session identity
- house back/close/page/tab controls
- rerender after frame changes and executed actions
- cleanup on close and disconnect

## House Behavior

The house style is owned by `menu-core`, not by examples or caller conventions.

- black pane filler is mandatory
- prompt-last is mandatory
- list and tab menus use the stable v1 footer grammar:
  - slot `45` = previous or back
  - slot `49` = close
  - slot `53` = next
  - remaining bottom-row footer slots = utility controls or filler
- tab menus reserve the top row for the tab strip
- progress blocks render with the fixed two-line house format

## Identity And Anti-Spoofing

Menu routing never relies on title text.

Paper:
- routing resolves through the owned `InventoryHolder` session object and the exact created top inventory instance
- stale or spoofed inventories with matching titles are ignored

Minestom:
- routing resolves through the per-viewer session and the exact opened `Inventory` instance
- stale or spoofed inventories with matching titles are ignored

## Dynamic Updates

Menu actions receive a `MenuContext`.

- `context.open(menu)` replaces the current menu for that viewer session
- `context.refresh()` rerenders the current compiled frame
- `context.close()` closes the current session

Use `context.open(...)` when the visible layout or content changes. `refresh()` is only for rerendering the current compiled menu.

## Paper Usage

```java
public final class MyPlugin extends JavaPlugin {

    private PaperMenuPlatform menus;

    @Override
    public void onEnable() {
        menus = new PaperMenuPlatform(this);
    }

    public void openProfile(Player player) {
        Menu menu = menus.list()
                .title("Profiles")
                .addItem(menus.button(Material.PLAYER_HEAD)
                        .name("Your SkyBlock Profile")
                        .description("View your equipment, stats, and more!")
                        .action(ActionVerb.VIEW, context -> context.open(profileDetail()))
                        .build())
                .build();
        menus.open(player, menu);
    }
}
```

The runnable Paper example is in:

- `platform/paper/paper-example`
- `sh.harold.creative.library.example.paper.PaperMenuExampleMenus`

Joining players automatically open the gallery menu in that example plugin.

## Minestom Usage

```java
MinecraftServer.init();
MinestomMenuPlatform menus = new MinestomMenuPlatform();

MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, event -> {
    if (event.isFirstSpawn()) {
        menus.open(event.getPlayer(), gallery(menus));
    }
});
```

The runnable Minestom example is in:

- `platform/minestom/minestom-example`
- `sh.harold.creative.library.example.minestom.MinestomMenuExampleMenus`

The bootstrap starts a small world and opens the gallery menu on first spawn.

## Representative Example Menus

Both example modules now include:

- `Your SkyBlock Profile`
- `Farming XLIX`
- `Museum Rewards`
- `Profile Slot #5`

Those four cards are exposed in a tabbed gallery and each opens into a dedicated preview menu.

## Known Limits

- chest-style inventories only
- menu row counts are currently `1..6`
- sessions are per viewer and per platform instance
- one active menu session per viewer per platform runtime
- `canvas` is fixed-slot in v1; viewport scrolling is not implemented yet
- `tabs` are tabbed-list only in v1
- there is no shared-inventory mode; opening the same menu for two viewers creates separate sessions
- menu content is still compiled eagerly; dynamic content changes should rebuild and reopen a menu
