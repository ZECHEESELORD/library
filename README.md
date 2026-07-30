# library

Shared modules for Minecraft servers, proxies, and mods. Paper and Minestom projects can use the same scoreboard API instead of maintaining separate implementations.

[![CI](https://github.com/ZECHEESELORD/library/actions/workflows/ci.yml/badge.svg)](https://github.com/ZECHEESELORD/library/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/ZECHEESELORD/library.svg)](https://jitpack.io/#ZECHEESELORD/library)
[![License: GPL v3](https://img.shields.io/badge/license-GPL--3.0--only-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%20and%2025-orange.svg)](#compatibility)
[![Platforms](https://img.shields.io/badge/platforms-Paper%2C%20Fabric%2C%20Minestom%2C%20Velocity-5b8a3a.svg)](#features)

The repository groups modules by feature. Public contracts and reusable logic do not depend on a host. Host code lives in adapter modules for Paper, Fabric, Minestom, and Velocity. Consumers compile against an API and include the adapter for their server.

---

## How the modules fit together

Each feature uses up to three layers. Artifact names match those layers:

| Suffix | Layer | What's in it |
| --- | --- | --- |
| `<feature>-api` | API | Interfaces, specs, and value types with no host code. Compile against this module. |
| `<feature>-core` | Core | Shared implementation with no Paper, Fabric, Minestom, or Velocity dependencies. |
| `<feature>-<platform>` | Adapter | Wiring that binds core to one host: `-paper`, `-minestom`, `-velocity`, or `-fabric`. |

The `tween`, `cooldown`, `state-machine`, and `metrics` features need only API and core modules because all of their logic is shared. Features that access players, worlds, or packets need an adapter for each supported platform.

> [!TIP]
> Compile against `-api`. At runtime, ship `-core` and one `-<platform>` adapter. Most consumers need no other modules.

---

## Features

Modules marked **common** work on any host without an adapter. Other rows list their available adapters.

### Spatial and world

| Module | What it does | Adapters |
| --- | --- | --- |
| `spatial` | Shared vectors, frames, anchors, bounds, and segments | common |
| `block-grid` | Positions and bounds aligned to blocks | Paper, Minestom |
| `block-boundary` | Region boundaries and rules that allow or deny crossings | Paper, Minestom |

### Motion and game feel

| Module | What it does | Adapters |
| --- | --- | --- |
| `tween` | Keyed interpolation with easing, envelopes, hold behavior, and repeat modes | common |
| `curve` | Catmull-Rom spline paths with sampling and splitting | common |
| `camera-motion` | Camera moves controlled by the server, with blend modes and playback for each viewer | Paper, Minestom |
| `trajectory-preview` | Projectile path previews with collision queries and recompute rules | Paper, Minestom |
| `impulse` | Knockback and dash impulses with stacking, compose modes, and axis masks | Paper, Minestom |
| `telegraph` | Attack and area warnings with configurable shapes, timing, and viewer scope | Paper, Minestom |
| `ambient-zone` | Overlapping ambient zones blended for each viewer along weight curves | Paper, Minestom |
| `screen-overlay` | Timed screen tints and vignettes with conflict rules | Paper, Minestom |

### State and timing

| Module | What it does | Adapters |
| --- | --- | --- |
| `tick-lifecycle` | Handles driven by ticks and the instance conflict policy shared by timed systems | common |
| `state-machine` | Typed reducer state machines with timers | common |
| `cooldown` | Reserve and query cooldown windows by scope and key | common |

### Data

| Module | What it does | Adapters |
| --- | --- | --- |
| `data` | Document persistence with `-memory`, `-yaml`, and `-mongodb` backends | Paper, Minestom, Velocity, Fabric |

### Player interaction

| Module | What it does | Adapters |
| --- | --- | --- |
| `message` | Adventure text with click and hover actions plus pagination | Paper, Minestom, Velocity, Fabric |
| `sound` | Sound playback API | Paper, Minestom, Fabric |
| `scoreboard` | Sidebar scoreboards built from sections, with viewer overrides and temporary sections | Paper, Minestom |
| `menu` | Compiled and reactive list, tabs, and canvas menus with adapter-owned whole-stack custody | Paper, Minestom, Fabric |
| `ui-values` | A small text value with an optional color, shared across the UI modules | common |

### Entities

| Module | What it does | Adapters |
| --- | --- | --- |
| `entity` | Entities composed from typed capabilities, including display, AI, equipment, pose, skin, variant, leash, and passenger support | Paper, Minestom, Fabric |
| `house-service-entity` | Service NPC implementation built on `entity-core` | common |

> [!NOTE]
> Paper has an optional `entity-paper-citizens` bridge for NPCs backed by Citizens. Velocity has no scoreboard adapter because a proxy cannot render a Minecraft sidebar.

> [!NOTE]
> `menu-paper` uses viewer- and region-aware scheduling and is compatible with Folia during normal operation. A consuming plugin must still declare `folia-supported: true` in its own plugin descriptor; this does not imply Folia support for unrelated Paper adapters. Folia hot-disable or reload with active menu sessions is unsupported because owner-thread work can no longer be scheduled after disable; disable only after those sessions are gone. Reactive runtimes skip native rendering after an unchanged reducer result. Keep high-frequency reactive titles stable: Paper and Fabric must rebuild and reopen the native inventory when a title changes.

### Observability

| Module | What it does | Adapters |
| --- | --- | --- |
| `metrics` | A `Telemetry` facade with a memory registry, JVM and process collectors, and Prometheus export through `-prometheus` | common |

---

## Install

Artifacts use this JitPack coordinate:

```text
com.github.ZECHEESELORD.library:<module>:<tag>
```

### Repositories

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Add the repositories required by the adapters you use:

| Repository | Needed for |
| --- | --- |
| `https://maven.fabricmc.net/` | Fabric adapters |
| `https://repo.papermc.io/repository/maven-public/` | Paper adapters |
| `https://maven.citizensnpcs.co/repo` | the Citizens bridge |
| `https://repo.minebench.de` | Velocity adapters |

### Coordinates

Import the BOM once, then name the modules without repeating the tag:

```kotlin
dependencies {
    implementation(platform("com.github.ZECHEESELORD.library:library-bom:v9"))

    implementation("com.github.ZECHEESELORD.library:cooldown-api")
    implementation("com.github.ZECHEESELORD.library:cooldown-core")
    implementation("com.github.ZECHEESELORD.library:scoreboard-api")
    implementation("com.github.ZECHEESELORD.library:scoreboard-core")
    implementation("com.github.ZECHEESELORD.library:scoreboard-paper")
    implementation("com.github.ZECHEESELORD.library:message-velocity")
}
```

> [!IMPORTANT]
> The BOM manages only artifacts from this repository. Host dependencies for Paper, Fabric, Minestom, Velocity, Citizens, and MongoDB still use their own repositories and the versions required by each module.

<details>
<summary>Full list of published artifacts</summary>

JitPack publishes:

- every `common/*` module, including the `-api`, `-core`, and backend artifacts listed above
- platform adapters under each host, such as `message-paper`, `scoreboard-minestom`, `menu-fabric`, `data-velocity`, and `entity-paper-citizens`

Legacy artifacts add a version suffix when their source differs from the current lane. Examples include `scoreboard-paper-1_21_11`, `message-fabric-1_21_11`, and `menu-fabric-1_21_11`.

Example modules (not published):

`menu-showcase`, `paper-example`, `paper-entity-example`, `minestom-example`, `minestom-entity-example`, `velocity-example`, `fabric-example`, `fabric-client-example`

</details>

---

## Compatibility

| Lane | Target | Java |
| --- | --- | --- |
| Common modules | any platform | 21 |
| Paper (latest) | Paper API `26.1.2.build.66-stable` (MC `26.1.2`) | 25 |
| Paper (legacy) | Paper API `1.21.11-R0.1-SNAPSHOT` | 21 |
| Fabric (latest) | MC `26.2`, Loader `0.19.3`, API `0.155.2+26.2` | 25 |
| Fabric (legacy) | MC `1.21.11`, Loader `0.18.6`, API `0.141.3+1.21.11` | 21 |
| Minestom | `2026.03.03-1.21.11` | 25 |
| Velocity | `3.3.0-SNAPSHOT` | 21 |

All text uses Adventure `4.17.0`.

> [!WARNING]
> The Java 25 lanes require a compatible toolchain. Use IntelliJ IDEA `2025.3` or later when working on the latest Paper and Fabric modules.

---

## Usage

Menu v9 authors semantic sections and leaves spacing, 240-pixel lore wrapping, progress rendering, and prompt placement to the compiler:

```java
MenuItemTemplate<AccountState> upgrade = MenuItemTemplate
        .<AccountState, UpgradeState>builder(MenuIcon.vanilla("gold_block"), AccountState::upgradeState)
        .base((state, item) -> item
                .name(Component.text("Gold Bank Upgrade", NamedTextColor.GOLD))
                .description("Increase the account balance limit and unlock another coop withdrawal slot.")
                .section(section -> section
                        .valueLine(Component.text("Bank cap: ", NamedTextColor.GRAY),
                                Component.text("100M coins", NamedTextColor.GOLD))
                        .mutedLine("Applies to every member of the profile."))
                .progress("Deposit requirement", state.deposited(), 5_000_000, "coins")
                .checklist(state.requirements()))
        .variant(UpgradeState.AVAILABLE, (state, item) -> item
                .status(Component.text("Ready to upgrade!", NamedTextColor.GREEN))
                .onLeftClick(ActionVerb.CONFIRM, "upgrade", context -> upgradeBank(state)))
        .variant(UpgradeState.LOCKED, (state, item) -> item
                .status(Component.text("Complete all requirements to upgrade.", NamedTextColor.RED)))
        .build();

Menu menu = menus.canvas()
        .title("Bank Upgrades")
        .rows(6)
        .place(22, upgrade.render(account))
        .build();
```

Domain terms such as coins, bank upgrades, and account requirements stay in consumer code. The shared API owns only the structural grammar.

This example registers and displays a scoreboard, overrides one section for a viewer, and adds a temporary notice:

```java
ScoreboardSpec board = ScoreboardSpec.builder(Key.key("example", "main"))
        .title(Component.text("Status"))
        .fixedSection("info", Component.text("Line 1"), Component.text("Line 2"))
        .section("activity", context -> List.of(currentLine(context)))
        .build();

scoreboards.register(board);
scoreboards.show(playerId, board.key());

scoreboards.overrideSection(
        playerId,
        "activity",
        ScoreboardSection.fixed("activity", Component.text("Temporary replacement"))
);

scoreboards.pushTransient(playerId, TransientSectionSpec.builder(Key.key("example", "notice"))
        .section(ScoreboardSection.fixed("notice", Component.text("Temporary notice")))
        .placement(TransientPlacement.TOP)
        .ttlTicks(60)
        .build());
```

The scoreboard API handles section order, viewer overrides, and transient sections measured in ticks. The consumer plugin supplies the domain data and meaning.

<details>
<summary>Metrics: timing, JVM gauges, and a Prometheus endpoint</summary>

Time a block of work under a label:

```java
LabelKey status = Metrics.label("status", "success", "failure");
TimerMetric chunkGeneration = Metrics.timer(
        "chunk_generation_seconds",
        "Tracks chunk generation latency",
        status
);

StandardTelemetry telemetry = new StandardTelemetry();
telemetry.observe(
        chunkGeneration,
        MetricLabels.of(status, "success"),
        () -> generateChunk(pos)
);
```

Start a Prometheus scrape endpoint in a plain JVM process:

```java
StandardTelemetry telemetry = new StandardTelemetry();
MetricRegistration jvmMetrics = JvmMetricsBinder.bind(telemetry);
PrometheusHttpExporter exporter = PrometheusHttpExporter.start(
        new InetSocketAddress("127.0.0.1", 9464),
        "/metrics",
        telemetry
);
```

Register a platform gauge without labels through the shared API:

```java
GaugeMetric playersOnline = Metrics.gauge(
        "players_online",
        "Current online player count",
        "players"
);

telemetry.registerGauge(playersOnline, MetricLabels.empty(), server::getPlayerCount);
```

The API uses explicit `observe(...)` calls, direct counter and gauge updates, and export wiring. It does not provide annotation instrumentation.

</details>

---

## Building from source

For faster work outside Fabric:

```bash
./gradlew build -PbuildProfile=nonFabric --configuration-cache
```

This profile leaves out Fabric projects and the BOM. Without Loom, the command can reuse Gradle's configuration cache. Use the full build for final platform verification:

```bash
./gradlew build
```

You need JDK 21 and JDK 25 because modules target both versions. The example modules are included in the build but are not published.

> [!NOTE]
> Fabric builds declare Minecraft through Loom's `minecraft(...)` configuration. Mods, libraries, and `project(...)` dependencies use standard Gradle configurations. Published artifacts come from the `jar` task, not `remapJar`.

---

## License

Licensed under [`GPL-3.0-only`](LICENSE).

> [!CAUTION]
> This is a copyleft license. If you distribute a fork or dependent work, follow the GPL v3 source requirements.
