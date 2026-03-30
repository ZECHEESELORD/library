---
name: menu-authoring
description: Author and review menus in this repo with the shared house-style menu API. Use when adding, changing, reviewing, or documenting menu code for Paper or Minestom, including choosing `list` vs `tabs` vs `canvas`, choosing `MenuButton` vs `MenuDisplayItem`, writing card content with the flat menu DSL, preserving prompt-last and house chrome, or touching menu runtime behavior that must keep owned session identity instead of title-based routing.
---

# Menu Authoring

Use the shared menu stack as a constrained house-style system, not as a generic inventory framework. The approved v1 authoring surface is `list`, `tabs`, `canvas`, the flat item DSL, and the built-in runtime/session model.

Menu copy inside this API belongs to the menu DSL. Do not route menu titles or card blocks through the message API. Use the `player-facing-messages` skill for chat, command, hover, help, and non-menu messaging surfaces.

## Geometry Choice

Use `list()` for browseable comparable entries.

- Use when items are peers and the menu is primarily about browsing, paging, or selecting from a collection.
- Expect a centered open `7x4` content panel in slots `10-16`, `19-25`, `28-34`, and `37-43`, with the footer house-owned.
- Do not use for pinned dashboards or detail pages.

Use `tabs()` for category switching across list-like panes.

- Use when row `0` should be a centered grouped tab strip and row `1` should be selection chrome under the visible tabs.
- Group boundaries are explicit; author groups intentionally instead of hoping the runtime infers them from names.
- Tabs default to the shared footer grammar, but may opt out only for custom canvas-style tab content.

Use `canvas()` for fixed-slot layouts.

- Use for previews, dashboards, detail pages, or pinned controls where explicit slot placement matters.
- Use `place(slot, item)` for the pinned content and let house chrome own its reserved footer slots.
- Do not use as the default browse geometry.

## House Rules

House style is mandatory in v1 and owned by `menu-core`.

- Black pane filler is the house default for chrome and canvas-style tab backgrounds; list-tab interiors stay open by default.
- Prompt-last is mandatory unless `skipPrompt()` is used deliberately on a `MenuButton`.
- Menu interactions use the stock sound FX library by default: ordinary actions use `menu/click`, page and tab-strip scroll chrome uses `menu/scroll`, and positive commit verbs such as `buy`, `claim`, and `confirm` use `result/confirm`.
- `list` and `tabs` use the stable footer grammar:
  - slot `45` = previous page
  - slot `48` = back
  - slot `49` = close
  - slot `53` = next
  - remaining footer slots = utilities or filler
- Shared back is runtime-owned, not caller-authored: `context.open(...)` pushes breadcrumb history, menus reached through that path render `&aGo Back` at slot `48`, and the single lore line is `&7To <menuname>` using the previous history entry's menu title.
- Root platform opens do not auto-show back, even though frame history may still exist internally.
- Close is always the simple shared `&cClose` button with no lore.
- Navigation arrows use the standard shared format: `&aPrevious Tab`, `&aNext Tab`, `&aPrevious Page`, or `&aNext Page`, with the single lore line `&ePage N`.
- For tab-strip arrows, `Page N` means the left-click destination tab window using 1-based numbering; disabled edge arrows clamp that number to the current end window.
- Pure `list()` menus use a centered open `7x4` panel at coordinates `(1,1)` through `(7,4)`; unused slots inside that panel stay blank by default instead of rendering filler.
- Pure `list()` menus append `(N/M)` to the rendered inventory title only when the list actually has more than one page.
- `tabs` centers the rendered tab cluster in row `0`; if the strip overflows, slots `0` and `8` become nav arrows and the visible slice is centered inside slots `1-7`.
- `tabs` uses row `1` for nav chrome: gray stained glass under visible inactive tabs, lime stained glass with glow under the visible active tab, and black filler for gaps, arrows, or padding.
- `tabs` accepts two content modes:
  - list content: bordered `3x7` panel in slots `19-25`, `28-34`, `37-43`, shared footer required; the panel interior stays open by default, so ordinary authored tabs should leave some negative space instead of forcing the full square, and overflowed tabs page through the footer arrows in slots `45` and `53`
  - canvas content: explicit slot placement below the nav rows, with row `5` caller-owned only when `customFooter()` is selected; canvas tabs keep black filler by default, and `CanvasBuilder.fillWithBlackPane(boolean)` can opt out when the authored layout needs open slots
- For canvas-style tabs, bias the primary authored placements onto row `3` and center them symmetrically: one item at `(4,3)`, two at `(2,3)` and `(6,3)`, then expand outward from the middle.
- Callers do not own border, filler, prompt ordering, or house chrome placement.

## Item Choice

Use `MenuButton` for interactive entries.

- A `MenuButton` must have at least one interaction.
- Default prompt generation uses the configured click verbs, always renders last, and ends the prompt with `!` such as `CLICK to view!`.
- Default sound selection comes from the action verb. Use `.sound(...)` or `.rightSound(...)` after defining the click interaction when a specific item should use a different stock cue such as `SoundCueKeys.RESULT_DENY`.
- If the stock menu cues are not enough for a special case, register or overlay your own cue in a `SoundCueService` and inject that service into the Paper or Minestom menu platform before opening menus.
- Use `.action(...)` for the common left-click path.
- Use `.onRightClick(...)` only when a second click path is genuinely needed.

Use `MenuDisplayItem` for read-only entries.

- Use it for previews, explanatory cards, and non-clickable chrome-adjacent information.
- Do not use it for something the viewer should click.

Use platform-native helpers in platform-authored code.

- Paper: `PaperMenuPlatform.button(Material)`, `display(Material)`, `tab(...)`
- Minestom: `MinestomMenuPlatform.button(Material)`, `display(Material)`, `tab(...)`
- Use raw `MenuIcon` builders only in host-neutral code, shared fixtures, or tests.
- Platform `tab(...)` helpers support both list-style item collections and canvas-style builders.

## Card Grammar

The public DSL is flat and block-oriented.

- `name(...)`: required title
- `secondary(...)`: short state/subtitle directly under the title
- `description(...)`: one wrapped paragraph block
- `line(...)` / `lines(...)`: freeform body lines
- `pair(...)` / `pairs(...)`: key/value body lines
- `bullet(...)` / `bullets(...)`: bullet list body lines
- `progress(...)`: fixed two-line house progress block

One singular call should usually mean one semantic block.

- `line("a").line("b")` creates two separated blocks.
- `lines("a", "b")` creates one contiguous block.
- The same rule applies to `pair/pairs` and `bullet/bullets`.

Prefer the smallest generic primitive that fits.

- Use `secondary(...)` for short state lines like `Unavailable` or `Selected Slot`.
- Use `pairs(...)` for compact fact or stat groups that belong together.
- Use singular `pair(...)` calls only when blank-line separation is intentional.
- Use `bullets(...)` for rewards, checklists, or grouped repeated lines.
- Use `progress(...)` whenever the content is actually progress; do not fake it with raw lines.

## Wrapping And Progress

Wrapping is owned by the compiler.

- Wrapping is character-count based, not font-metric based.
- `secondary(...)` and `description(...)` auto-wrap with balanced line fitting: keep one line when the text still fits within `30` characters, otherwise rebalance into the fewest reasonably even lines under that cap with `20` as the soft target.
- `bullet(...)` soft-wraps with a hanging continuation indent.
- `line(...)` and `pair(...)` are single-line by default.
- The item title never widens lore wrapping; only the lore text itself matters.
- Use `softLine`, `softLines`, `softPair`, or `softPairs` only for genuinely long dynamic values such as gear names or value-heavy lines.
- Multi-entry `lines(...)` and `pairs(...)` blocks stay one entry per lore line even when authored through a soft variant; use the soft variants for a single long entry, not a stat list.
- If balanced fitting still reads awkwardly, rewrite the authored copy instead of forcing manual line breaks into the DSL.

Progress is a special house block.

- `progress(label, current, max)` defaults to `AccentFamily.GOLD`.
- Use the accent-family overload only with the approved families.
- Pass numbers, not preformatted strings.
- The compiler formats grouped numbers and percent output for you.
- The bar line uses house-owned strikethrough styling across both the filled and empty segments to read as one continuous bar.
- Progress does not use generic lore wrapping.

## Runtime And Compiled Model

Menus compile eagerly into frames when `build()` is called.

- Treat `Menu` as compiled output, not a live mutable inventory object.
- If content or geometry changes, rebuild the menu and call `context.open(newMenu)`.
- Use `context.refresh()` only to rerender the current compiled frame.
- The runtime may still overlay session-owned house chrome such as breadcrumb back while rendering the active frame.
- Use `context.close()` to close the active menu session.

The runtime owns identity and routing.

- Never identify or validate a menu by title text.
- Paper routes through owned `InventoryHolder` session identity and the created top inventory instance.
- Minestom routes through the owned inventory instance plus the per-viewer session.
- One viewer has one active menu session per platform runtime in v1.

## Warnings And Anti-Patterns

- Do not use `canvas()` as the default when a `list()` would do.
- Do not use `tabs()` unless the grouped tab strip and row-`1` nav chrome are part of the real interaction model.
- Do not expect the runtime to invent logical tab groups from tab names or icon themes.
- Do not pack showcase/example list tabs edge-to-edge unless you are intentionally testing tab paging.
- Do not scatter canvas-tab controls arbitrarily when a centered row-`3` composition would communicate the layout more clearly.
- Do not bypass the stock menu sound defaults by hard-coding host sound calls inside menu actions; override the interaction cue or inject a custom `SoundCueService` instead.
- Do not use `MenuDisplayItem` for clickable entries.
- Do not suppress prompts casually with `skipPrompt()`.
- Do not hand-author chrome slots that belong to the house footer.
- Do not try to author your own shared footer back button; use `context.open(...)` transitions and let the runtime breadcrumb model own it.
- Do not use `customFooter()` with list-style tab content.
- Do not assume nav arrows change the active tab; they only scroll the visible strip.
- Do not use repeated singular builders when the content is one logical group.
- Do not reach for `soft*` variants unless long dynamic content actually needs them.
- Do not assume `refresh()` recompiles content.
- Do not route or guard runtime behavior by title text.
- Do not treat this library as a general arbitrary-style inventory framework.

## v1 Boundaries

- Paper and Minestom only
- no Velocity menu backend
- chest-style menus only
- rows `1..6`
- `tabs()` uses explicit groups, row-`1` highlight chrome, list or canvas tab content, and shared footer by default
- `canvas()` is fixed-slot only
- one mandatory house style
- no raw lore escape hatch
- no alternate theme system
- no shared viewer session mode

## References

- Read [references/examples.md](references/examples.md) for cookbook examples and rendered snapshots.
