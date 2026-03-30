# Menu Authoring Examples

These examples show the approved house-style usage of the current v1 menu stack.

Rendered snapshots below are flattened plain-text views of the compiled result. They show block spacing, wrapping, prompt placement, and layout shape. Color and accent segmentation are omitted from the plain-text snapshots unless called out explicitly.

Examples use Paper snippets for brevity. Minestom uses the same shape through `MinestomMenuPlatform` with native Minestom `Material` values.

## Choose The Geometry

| Builder | Use when | Avoid when |
| --- | --- | --- |
| `list()` | Browseable peer entries, paging, selection | Fixed-slot dashboards or detail pages |
| `tabs()` | Category switching across list-like panes | Each tab needs its own custom layout |
| `canvas()` | Pinned layouts, detail pages, preview cards | General browsing or automatic paging |

## Your SkyBlock Profile

Why this builder:

- Use a `MenuButton` because this is a clickable browse entry.
- Use `list()` because it belongs in a gallery of peer profile-related entries.
- Use `lines(...)` for the stat cluster because it is one contiguous block.

```java
Menu menu = menus.list()
        .title("Profiles")
        .addItem(menus.button(Material.PLAYER_HEAD)
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
                .action(ActionVerb.VIEW, context -> context.open(profileDetail()))
                .build())
        .build();
```

Rendered card:

```text
Your SkyBlock Profile
View your equipment,
stats, and more!

✦ Speed 361
❁ Strength 399.75
❈ Defense 786
☠ Crit Damage 306.5%
☣ Crit Chance 144.5%
❤ Health 2,524
✎ Intelligence 1,563.14
and more...

Also accessible via /stats

CLICK to view
```

## Farming XLIX

Why this builder:

- Use `progress(...)` because the card has real progress, not just two arbitrary lore lines.
- Use `bullets(...)` because the reward lines form one grouped block.
- Use `MenuButton` because this is an actionable browse entry.

```java
menus.button(Material.GOLDEN_HOE)
        .name("Farming XLIX")
        .description("Harvest crops and shear sheep to earn Farming XP!")
        .progress("Progress to Level L", 3_432_908.3, 4_000_000, AccentFamily.GOLD)
        .bullets(
                "Farmhand L",
                "Grants +196 to +200 Farming Fortune",
                "+5 Health",
                "+1,000,000 Coins",
                "+20 SkyBlock XP")
        .action(ActionVerb.VIEW, context -> context.open(farmingDetail()))
        .build();
```

Rendered card:

```text
Farming XLIX
Harvest crops and
shear sheep to
earn Farming XP!

Progress to Level L: 85.8%
-------------------- 3,432,908.3/4,000,000

• Farmhand L
• Grants +196 to
  +200 Farming
  Fortune
• +5 Health
• +1,000,000 Coins
• +20 SkyBlock XP

CLICK to view
```

Notes:

- The plain snapshot hides the accent-family colors.
- The real progress block uses the fixed two-line house pattern and house-owned bar width.

## Museum Rewards

Why this builder:

- Use `pairs(...)` because `Total XP` and `Milestone` are one compact grouped fact block.
- Use singular `line(...)` for the warning because it should stand as its own block.
- Keep `progress(...)` separate instead of assembling your own bar lines.

```java
menus.button(Material.BOOK)
        .name("Museum Rewards")
        .description("Every 100 SkyBlock XP obtained from your Museum, Eleanor will reward you.")
        .line("Special Items do not reward SkyBlock XP.")
        .pairs(
                "Total XP", "395/3,522",
                "Milestone", "3/40")
        .progress("Progress to Milestone 4", 35, 100, AccentFamily.AQUA)
        .action(ActionVerb.VIEW, context -> context.open(museumDetail()))
        .build();
```

Rendered card:

```text
Museum Rewards
Every 100 SkyBlock XP
obtained from your Museum,
Eleanor will reward you.

Special Items do not reward SkyBlock XP.

Total XP: 395/3,522
Milestone: 3/40

Progress to Milestone 4: 35%
-------------------- 35/100

CLICK to view
```

## Profile Slot #5

Why this builder:

- Use `secondary(...)` for the short state line.
- Use singular `pair(...)` calls because the blank-line separation is intentional here.
- Use singular `line(...)` for the status note so it remains its own block.

```java
menus.button(Material.GRAY_DYE)
        .name("Profile Slot #5")
        .secondary("Unavailable")
        .pair("Cost", "2,750 SkyBlock Gems")
        .pair("You have", "360 Gems")
        .line("Cannot afford this!")
        .action(ActionVerb.OPEN, context -> context.open(gemStore()))
        .build();
```

Rendered card:

```text
Profile Slot #5
Unavailable

Cost: 2,750 SkyBlock Gems

You have: 360 Gems

Cannot afford this!

CLICK to open
```

## Tabs Gallery

Why this builder:

- Use `tabs()` when row `0` should be a centered grouped strip and the categories are real authored groups, not accidental naming conventions.
- Mix sparse list tabs with canvas tabs when the gallery needs both browseable entries and custom explanatory layouts.
- Keep ordinary showcase list tabs under the full `3x7` capacity so the bordered panel stays readable; use a separate overflow case when you want to test footer paging.
- For canvas tabs, bias the main placements onto row `3` and center them first: `(4,3)` for one item, `(2,3)` and `(6,3)` for two, then expand outward.

```java
Menu menu = menus.tabs()
        .title("House Style Gallery")
        .defaultTab("profiles")
        .addGroup(MenuTabGroup.of("account", List.of(
                menus.tab("profiles", "Profiles", Material.PLAYER_HEAD, List.of(
                        yourSkyBlockProfileButton(),
                        profileSlotFiveButton(),
                        accessoryBagButton(),
                        wardrobeButton())),
                menus.tab("progress", "Progress", Material.EXPERIENCE_BOTTLE, List.of(
                        farmingXlixButton(),
                        museumRewardsButton(),
                        slayerButton(),
                        collectionsButton())))))
        .addGroup(MenuTabGroup.of("showcase", List.of(
                menus.tab("overview", "Overview", Material.COMPASS, builder -> {
                        if (!canvasFillerEnabled) {
                                builder.fillWithBlackPane(false);
                        }
                        builder.place(31, centeredCanvasInfo());
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
                        builder.place(29, openListGalleryButton());
                        builder.place(31, canvasFillerToggleButton(canvasFillerEnabled, "routes"));
                        builder.place(33, openCanvasGalleryButton());
                })))
        .build();
```

Rendered layout snapshot:

```text
GEOMETRY TABS
INITIAL tab:profiles:nav:0:page:0
FRAME tab:profiles:nav:0:page:0
0:Previous Tabs
1:Profiles
2:Progress
3:Museum
4:Upgrades
5:[gap]
6:Party
7:Guild
8:Next Tabs
10:[lime indicator]
15:[gray indicator]
19:Your SkyBlock Profile
20:[air]
49:Close
```

Notes:

- Row `0` is centered group-aware tab chrome.
- Row `1` is state chrome under visible tabs, not a generic spacer row.
- When the strip overflows, slots `0` and `8` become simple scroll arrows; left click scrolls by one and right click jumps to the start/end.
- List-style tab content uses the bordered `3x7` panel starting at slot `19`; its interior is open by default, so ordinary showcase tabs should usually leave some negative space instead of packing every slot.
- Canvas-style tab content keeps black filler by default and should usually rely on that default; call `builder.fillWithBlackPane(false)` only when the authored layout should expose open slots instead.
- Canvas-style tab examples should center their primary authored content on row `3` before expanding into surrounding rows.
- If you want to exercise footer paging explicitly, build a list-style tab with more than `27` example items; the shared footer will surface previous/next arrows in the bottom corners.

## Canvas Preview

Why this builder:

- Use `canvas()` for pinned preview or detail layouts.
- Use `MenuDisplayItem` for the non-clickable preview card.
- Use `context.open(...)` to reach this preview from its parent menu; the shared breadcrumb back button will appear automatically when the viewer opens it that way.

```java
Menu menu = menus.canvas()
        .title("Museum Rewards Preview")
        .place(13, menus.display(Material.BOOK)
                .name("Museum Rewards")
                .description("Every 100 SkyBlock XP obtained from your Museum, Eleanor will reward you.")
                .line("Special Items do not reward SkyBlock XP.")
                .pairs(
                        "Total XP", "395/3,522",
                        "Milestone", "3/40")
                .progress("Progress to Milestone 4", 35, 100, AccentFamily.AQUA)
                .build())
        .build();
```

Rendered layout snapshot:

```text
GEOMETRY CANVAS
13:Museum Rewards
48:Go Back
49:Close
```

Rendered preview card:

```text
Museum Rewards
Every 100 SkyBlock XP
obtained from your Museum,
Eleanor will reward you.

Special Items do not reward SkyBlock XP.

Total XP: 395/3,522
Milestone: 3/40

Progress to Milestone 4: 35%
-------------------- 35/100
```

## Anti-Patterns

Do not do these:

- Do not choose `canvas()` just to place ordinary browse entries at fixed slots.
- Do not choose `tabs()` when a single `list()` plus paging is enough.
- Do not use `MenuDisplayItem` for entries that should open something.
- Do not use repeated singular builders when the lines are one logical group.
- Do not use `soft*` variants preemptively on ordinary short text.
- Do not assemble your own fake progress block with `line(...)`.
- Do not suppress prompts casually with `skipPrompt()`.
- Do not assume `context.refresh()` rebuilds a card with new content.
- Do not treat menu titles as runtime identity.
